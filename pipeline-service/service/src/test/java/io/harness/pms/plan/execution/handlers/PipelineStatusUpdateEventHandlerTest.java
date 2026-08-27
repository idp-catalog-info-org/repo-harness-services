/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.ExecutionSummaryUpdateInfo;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryServiceImpl;
import io.harness.repositories.executions.GraphUpdateInfoRepository;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.waiter.WaitNotifyEngine;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

public class PipelineStatusUpdateEventHandlerTest extends PipelineServiceTestBase {
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private OrchestrationEventEmitter eventEmitter;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PmsExecutionSummaryServiceImpl pmsExecutionSummaryServiceImpl;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private GraphUpdateInfoRepository graphUpdateInfoRepository;
  private PipelineStatusUpdateEventHandler pipelineStatusUpdateEventHandler;

  @Before
  public void setUp() throws Exception {
    pipelineStatusUpdateEventHandler =
        new PipelineStatusUpdateEventHandler(planExecutionService, pmsExecutionSummaryRepository, eventEmitter,
            waitNotifyEngine, pmsExecutionSummaryServiceImpl, pmsFeatureFlagHelper, graphUpdateInfoRepository);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldOnEndEmitEventsWhenExecutedModulesHasNullElements() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = createPipelineExecutionSummaryEntity();
    when(pmsExecutionSummaryRepository.findByPlanExecutionIdAndPipelineDeletedNot("planExecutionId", true))
        .thenReturn(Optional.of(pipelineExecutionSummaryEntity));
    when(pmsExecutionSummaryRepository.update(notNull(), notNull())).thenReturn(pipelineExecutionSummaryEntity);
    Ambiance ambiance = createAmbiance();
    PlanExecution planExecution = PlanExecution.builder().status(Status.SUCCEEDED).endTs(100L).build();
    doReturn(planExecution)
        .when(planExecutionService)
        .getWithFieldsIncluded(
            ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.endTs, PlanExecutionKeys.status));

    pipelineStatusUpdateEventHandler.onEnd(ambiance, Status.SUCCEEDED);
    ArgumentCaptor<OrchestrationEvent> argumentCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    // IN THIS SCENARIO WE ARE ONLY VERIFYING THAT EVENT WAS EMITTED TWICE AND WITHOUT NPE
    // A NEW TEST CASE SHOULD BE CREATED TO ASSERT EMITTED EVENT PROPERTIES.
    verify(eventEmitter, times(2)).emitEvent(argumentCaptor.capture());

    OrchestrationEvent event = argumentCaptor.getValue();
    assertThat(event.getEndTs()).isEqualTo(planExecution.getEndTs());
    assertThat(event.getStatus())
        .isEqualTo(ExecutionStatus.getExecutionStatus(planExecution.getStatus()).getEngineStatus());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldOnEndEmitEventsWhenExecutedModulesHasInProgressStatuses() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = createPipelineExecutionSummaryEntity();
    when(pmsExecutionSummaryRepository.findByPlanExecutionIdAndPipelineDeletedNot("planExecutionId", true))
        .thenReturn(Optional.of(pipelineExecutionSummaryEntity));
    when(graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategory(
             "planExecutionId", StepCategory.PIPELINE))
        .thenReturn(Optional.of(createGraphUpdateInfo()));
    when(pmsExecutionSummaryRepository.update(notNull(), notNull())).thenReturn(pipelineExecutionSummaryEntity);
    Ambiance ambiance = createAmbiance();
    PlanExecution planExecution = PlanExecution.builder().status(Status.SUCCEEDED).endTs(100L).build();
    doReturn(planExecution)
        .when(planExecutionService)
        .getWithFieldsIncluded(
            ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.endTs, PlanExecutionKeys.status));

    pipelineStatusUpdateEventHandler.onEnd(ambiance, Status.SUCCEEDED);
    ArgumentCaptor<OrchestrationEvent> argumentCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    // IN THIS SCENARIO WE ARE VERIFYING THAT EVENT WAS EMITTED 3 TIMES BECAUSE THE EVENT IS EMITTED TO CI MODULE
    // ALWAYS.
    verify(eventEmitter, times(3)).emitEvent(argumentCaptor.capture());

    OrchestrationEvent event = argumentCaptor.getValue();
    assertThat(event.getEndTs()).isEqualTo(planExecution.getEndTs());
    assertThat(event.getStatus())
        .isEqualTo(ExecutionStatus.getExecutionStatus(planExecution.getStatus()).getEngineStatus());
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testOnPlanStatusUpdate() {
    Ambiance ambiance = createAmbiance();
    PlanExecution planExecution = PlanExecution.builder().status(Status.APPROVAL_WAITING).build();
    doReturn(planExecution).when(planExecutionService).getPlanExecutionMetadata(ambiance.getPlanExecutionId());
    pipelineStatusUpdateEventHandler.onPlanStatusUpdate(ambiance);
    ArgumentCaptor<String> planExecutionIdArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<PlanExecution> planExecutionArgumentCaptor = ArgumentCaptor.forClass(PlanExecution.class);
    verify(pmsExecutionSummaryServiceImpl, times(1))
        .updatePlanExecutionSummaryStatus(
            planExecutionIdArgumentCaptor.capture(), planExecutionArgumentCaptor.capture());
    assertThat(planExecutionIdArgumentCaptor.getValue()).isEqualTo(ambiance.getPlanExecutionId());
    assertThat(planExecutionArgumentCaptor.getValue()).isEqualTo(planExecution);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testOnPlanStatusUpdate_skipsWhenCdcGraphEnabled() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgIdentifier")
            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projectIdentifier")
            .setPlanExecutionId("planExecutionId")
            .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIPE_USE_CDC_BASED_GRAPH.name(), true)
                             .build())
            .build();
    pipelineStatusUpdateEventHandler.onPlanStatusUpdate(ambiance);

    verify(planExecutionService, times(0)).getPlanExecutionMetadata(ambiance.getPlanExecutionId());
    verify(pmsExecutionSummaryServiceImpl, times(0)).updatePlanExecutionSummaryStatus(any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void shouldNotEmitEndEventsForNullCIModuleInfo() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = createPipelineExecutionSummaryEntityWithCIModule();
    when(pmsExecutionSummaryRepository.findByPlanExecutionIdAndPipelineDeletedNot("planExecutionId", true))
        .thenReturn(Optional.of(pipelineExecutionSummaryEntity));
    when(graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategory(
             "planExecutionId", StepCategory.PIPELINE))
        .thenReturn(Optional.of(createGraphUpdateInfoWithNullCIModule()));
    when(pmsExecutionSummaryRepository.update(notNull(), notNull())).thenReturn(pipelineExecutionSummaryEntity);
    Ambiance ambiance = createAmbiance();
    PlanExecution planExecution = PlanExecution.builder().status(Status.SUCCEEDED).endTs(100L).build();
    doReturn(planExecution)
        .when(planExecutionService)
        .getWithFieldsIncluded(
            ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.endTs, PlanExecutionKeys.status));

    pipelineStatusUpdateEventHandler.onEnd(ambiance, Status.SUCCEEDED);
    ArgumentCaptor<OrchestrationEvent> argumentCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    // Previously also it was 2 times because in case of CI being retrieved from layoutnodemap, the event wasn't getting
    // sent by emitEventToCIModule, it was failing before event is sent So the loop outer to it would be sending the
    // event
    verify(eventEmitter, times(2)).emitEvent(argumentCaptor.capture());

    List<OrchestrationEvent> event = argumentCaptor.getAllValues();
    assertThat(event.get(0).getModuleInfo()).isEmpty();
    assertThat(event.get(0).getEndTs()).isEqualTo(planExecution.getEndTs());
    assertThat(event.get(0).getStatus())
        .isEqualTo(ExecutionStatus.getExecutionStatus(planExecution.getStatus()).getEngineStatus());
    assertThat(event.get(1).getModuleInfo()).isEmpty();
    assertThat(event.get(1).getEndTs()).isEqualTo(planExecution.getEndTs());
    assertThat(event.get(1).getStatus())
        .isEqualTo(ExecutionStatus.getExecutionStatus(planExecution.getStatus()).getEngineStatus());

    Mockito.clearInvocations(eventEmitter);
    when(graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategory(
             "planExecutionId", StepCategory.PIPELINE))
        .thenReturn(Optional.of(createGraphUpdateInfo()));
    pipelineStatusUpdateEventHandler.onEnd(ambiance, Status.SUCCEEDED);
    // Now after the fix one event will be sent by emitEventToCIModule, and another by the outer loop
    ArgumentCaptor<OrchestrationEvent> argumentCaptor2 = ArgumentCaptor.forClass(OrchestrationEvent.class);
    verify(eventEmitter, times(2)).emitEvent(argumentCaptor2.capture());
    event = argumentCaptor2.getAllValues();
    assertThat(event.get(0).getModuleInfo().toStringUtf8())
        .isEqualTo("{\"attributeA\":\"foo\",\"attributeB\":\"bar\"}");
    assertThat(event.get(0).getEndTs()).isEqualTo(planExecution.getEndTs());
    assertThat(event.get(0).getStatus())
        .isEqualTo(ExecutionStatus.getExecutionStatus(planExecution.getStatus()).getEngineStatus());
    assertThat(event.get(1).getModuleInfo()).isEmpty();
    assertThat(event.get(1).getEndTs()).isEqualTo(planExecution.getEndTs());
    assertThat(event.get(1).getStatus())
        .isEqualTo(ExecutionStatus.getExecutionStatus(planExecution.getStatus()).getEngineStatus());
  }

  private Ambiance createAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgIdentifier")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projectIdentifier")
        .setPlanExecutionId("planExecutionId")
        .build();
  }
  // CREATE ENTITY CONTAINING NULL MODULES TO FORCE THE NPE DURING THE EXECUTION.
  private PipelineExecutionSummaryEntity createPipelineExecutionSummaryEntity() {
    Map<String, GraphLayoutNodeDTO> layoutNode = new HashMap<>();
    layoutNode.put(
        "keyA", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module("moduleA").build());
    layoutNode.put(
        "keyB", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module("moduleB").build());
    layoutNode.put("keyC",
        GraphLayoutNodeDTO.builder()
            .status(ExecutionStatus.SUCCESS)
            .skipInfo(SkipInfo.newBuilder().setEvaluatedCondition(true).build())
            .module(null)
            .build());
    layoutNode.put(
        "keyD", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module(null).build());
    layoutNode.put(
        "keyE", GraphLayoutNodeDTO.builder().status(ExecutionStatus.WAITING).skipInfo(null).module("moduleE").build());
    layoutNode.put(
        "ci", GraphLayoutNodeDTO.builder().status(ExecutionStatus.WAITING).skipInfo(null).module("ci").build());

    Map<String, Document> moduleInfo = new HashMap<>();
    moduleInfo.put("moduleA", null);
    moduleInfo.put("moduleB", null);
    moduleInfo.put("ci", null);

    return PipelineExecutionSummaryEntity.builder()
        .layoutNodeMap(layoutNode)
        .moduleInfo(moduleInfo)
        .status(ExecutionStatus.SUCCESS)
        .parentStageInfo(PipelineStageInfo.newBuilder().setHasParentPipeline(true).build())
        .endTs(4321L)
        .build();
  }

  private GraphUpdateInfo createGraphUpdateInfo() {
    Map<String, LinkedHashMap<String, Object>> moduleInfo = new HashMap<>();
    LinkedHashMap<String, Object> moduleData = new LinkedHashMap<>();
    moduleData.put("attributeA", "foo");
    moduleData.put("attributeB", "bar");
    moduleInfo.put("ci", moduleData);

    return GraphUpdateInfo.builder()
        .executionSummaryUpdateInfo(ExecutionSummaryUpdateInfo.builder().moduleInfo(moduleInfo).build())
        .build();
  }

  private GraphUpdateInfo createGraphUpdateInfoWithNullCIModule() {
    Map<String, LinkedHashMap<String, Object>> moduleInfo = new HashMap<>();
    moduleInfo.put("ci", null);

    return GraphUpdateInfo.builder()
        .executionSummaryUpdateInfo(ExecutionSummaryUpdateInfo.builder().moduleInfo(moduleInfo).build())
        .build();
  }

  private PipelineExecutionSummaryEntity createPipelineExecutionSummaryEntityWithCIModule() {
    Map<String, GraphLayoutNodeDTO> layoutNode = new HashMap<>();
    layoutNode.put(
        "id1", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module("cd").build());
    layoutNode.put(
        "id2", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module("cd").build());
    layoutNode.put(
        "id3", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module("ci").build());

    Map<String, Document> moduleInfo = new HashMap<>();
    moduleInfo.put("cd", null);
    moduleInfo.put("ci", null);

    return PipelineExecutionSummaryEntity.builder()
        .layoutNodeMap(layoutNode)
        .moduleInfo(moduleInfo)
        .status(ExecutionStatus.SUCCESS)
        .parentStageInfo(PipelineStageInfo.newBuilder().setHasParentPipeline(true).build())
        .endTs(4321L)
        .build();
  }
}
