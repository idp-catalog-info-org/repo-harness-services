/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.beans.FeatureName.DISABLE_PIPELINE_EXECUTION_GIT_METADATA_UPSERT;
import static io.harness.beans.FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION;
import static io.harness.beans.FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.gitmetadata.service.PipelineExecutionGitMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.observers.beans.OrchestrationQueueInfo;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.plan.Plan;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.DependencyEntry;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.GraphLayoutInfo;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.plan.StringArray;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.notification.helper.NotificationHelper;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.metadata.RecentExecutionsInfoHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionSummaryCreateEventHandlerTest extends PipelineServiceTestBase {
  @Mock private PMSPipelineService pmsPipelineService;
  @Mock private PlanService planService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private NodeTypeLookupService nodeTypeLookupService;
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  @Mock private NotificationHelper notificationHelper;
  @Mock private RecentExecutionsInfoHelper recentExecutionsInfoHelper;
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock PipelineExecutionGitMetadataService gitMetadataService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;

  private ExecutionSummaryCreateEventHandler executionSummaryCreateEventHandler;

  private final String ACCOUNT_IDENTIFIER = "accId";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJECT_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "pipelineId";

  @Before
  public void setUp() throws Exception {
    executionSummaryCreateEventHandler = new ExecutionSummaryCreateEventHandler(pmsPipelineService, planService,
        planExecutionService, nodeTypeLookupService, pmsGitSyncHelper, notificationHelper, recentExecutionsInfoHelper,
        pmsExecutionSummaryService, pmsExecutionService, gitMetadataService, pmsFeatureFlagService);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnStart() {
    String inputSetYaml = "some-yaml";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(ambiance.getPlanExecutionId())
                                                      .inputSetYaml(inputSetYaml)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .executionInputConfigured(true)
                                                      .allowStagesExecution(true)
                                                      .notes("notes")
                                                      .build();
    StagesExecutionMetadata stagesExecutionMetadata = StagesExecutionMetadata.builder().isStagesExecution(true).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .stagesExecutionMetadata(stagesExecutionMetadata)
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .build();
    Map<String, String> setupAbstractions = new HashMap<>(ambiance.getSetupAbstractionsMap());
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(setupAbstractions)
                                      .metadata(ExecutionMetadata.newBuilder()
                                                    .setRunSequence(1)
                                                    .setPipelineIdentifier("pipelineId")
                                                    .setExecutionMode(ExecutionMode.NORMAL)
                                                    .build())
                                      .stagesExecutionMetadata(stagesExecutionMetadata)
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    ArgumentCaptor<ExecutionSummaryInfo> executionSummaryInfoArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionSummaryInfo.class);

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(planService.fetchPlan(planId))
        .thenReturn(Plan.builder()
                        .graphLayoutInfo(GraphLayoutInfo.newBuilder()
                                             .setStartingNodeId("startId")
                                             .putLayoutNodes("startId",
                                                 GraphLayoutNode.newBuilder().setNodeGroup("node-group").build())
                                             .build())
                        .build());

    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    doNothing()
        .when(pmsPipelineService)
        .saveExecutionInfo(any(), anyString(), executionSummaryInfoArgumentCaptor.capture(), anyBoolean());

    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());

    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    ExecutionSummaryInfo executionSummaryInfo = executionSummaryInfoArgumentCaptor.getValue();
    assertThat(executionSummaryInfo.getLastExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(executionSummaryInfo.getNumOfErrors()).isEmpty();
    assertThat(executionSummaryInfo.getDeployments()).isNotEmpty();
    assertThat(executionSummaryInfo.getDeployments().get(getFormattedDate())).isEqualTo(1);
    assertThat(executionSummaryInfo.getLastExecutionId()).isEqualTo(ambiance.getPlanExecutionId());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity.getNotesExistForPlanExecutionId()).isTrue();
    assertThat(capturedEntity).isNotNull();
    assertThat(capturedEntity.getRunSequence()).isEqualTo(1);
    assertThat(capturedEntity.getPipelineIdentifier()).isEqualTo("pipelineId");
    assertThat(capturedEntity.getPlanExecutionId()).isEqualTo(ambiance.getPlanExecutionId());
    assertThat(capturedEntity.getPipelineDeleted()).isFalse();
    assertThat(capturedEntity.getInternalStatus()).isEqualTo(null);
    assertThat(capturedEntity.getStatus()).isEqualTo(ExecutionStatus.NOTSTARTED);
    assertThat(capturedEntity.getTags()).isEmpty();
    assertThat(capturedEntity.getStartingNodeId()).isEqualTo("startId");
    assertThat(capturedEntity.getModules()).containsExactly("pms", "common");
    assertThat(capturedEntity.getLayoutNodeMap()).isNotEmpty();
    assertThat(capturedEntity.getLayoutNodeMap()).containsKeys("startId");
    assertThat(capturedEntity.getStagesExecutionMetadata().isStagesExecution()).isTrue();
    assertThat(capturedEntity.isStagesExecutionAllowed()).isTrue();
    assertThat(capturedEntity.getExecutionInputConfigured())
        .isEqualTo(planExecutionMetadata.getExecutionInputConfigured());
    assertThat(capturedEntity.getStoreType()).isNull();
    assertThat(capturedEntity.getConnectorRef()).isNull();
    assertThat(capturedEntity.getExecutionMode()).isEqualTo(ExecutionMode.NORMAL);
    assertThat(capturedEntity.getRollbackModeExecutionId()).isNull();

    verify(pmsPipelineService, times(1))
        .getPipeline(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, PIPELINE_IDENTIFIER, false, true, false,
            false,
            ScopeInfo.builder()
                .accountIdentifier(ACCOUNT_IDENTIFIER)
                .orgIdentifier(ORG_IDENTIFIER)
                .projectIdentifier(PROJECT_IDENTIFIER)
                .uniqueId("unique-id")
                .build(),
            true);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestOnStartOfQueuedPlanCreation() {
    String inputSetYaml = "some-yaml";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setRunSequence(1)
                    .setPipelineIdentifier("pipelineId")
                    .putFeatureFlagToValueMap(PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION.name(), true)
                    .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build())
                    .setExecutionMode(ExecutionMode.NORMAL)
                    .build())
            .build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(ambiance.getPlanExecutionId())
                                                      .inputSetYaml(inputSetYaml)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .executionInputConfigured(true)
                                                      .allowStagesExecution(true)
                                                      .notes("notes")
                                                      .build();
    StagesExecutionMetadata stagesExecutionMetadata = StagesExecutionMetadata.builder().isStagesExecution(true).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .stagesExecutionMetadata(stagesExecutionMetadata)
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .isAsyncPlanCreation(true)
            .build();
    PlanExecution planExecution =
        PlanExecution.builder()
            .metadata(ExecutionMetadata.newBuilder()
                          .setRunSequence(1)
                          .setPipelineIdentifier("pipelineId")
                          .setExecutionMode(ExecutionMode.NORMAL)
                          .putFeatureFlagToValueMap(PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION.name(), true)
                          .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build())
                          .build())
            .stagesExecutionMetadata(stagesExecutionMetadata)
            .build();
    ArgumentCaptor<Update> pipelineExecutionSummaryEntityArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(planService.fetchPlan(planId))
        .thenReturn(Plan.builder()
                        .graphLayoutInfo(GraphLayoutInfo.newBuilder()
                                             .setStartingNodeId("startId")
                                             .putLayoutNodes("startId",
                                                 GraphLayoutNode.newBuilder().setNodeGroup("node-group").build())
                                             .build())
                        .build());
    doReturn(null)
        .when(pmsExecutionSummaryService)
        .update(eq(planExecutionId), pipelineExecutionSummaryEntityArgumentCaptor.capture());
    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");
    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());
    Update capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    Document updateDoc = (Document) capturedEntity.getUpdateObject().get("$set");
    assertThat((Boolean) updateDoc.get(PlanExecutionSummaryKeys.notesExistForPlanExecutionId)).isTrue();
    assertThat(updateDoc.get(PlanExecutionSummaryKeys.governanceMetadata)).isNull();
    assertThat(updateDoc.get(PlanExecutionSummaryKeys.internalStatus)).isEqualTo(null);
    assertThat(updateDoc.get(PlanExecutionSummaryKeys.status)).isEqualTo(ExecutionStatus.NOTSTARTED);
    assertThat(updateDoc.get(PlanExecutionSummaryKeys.startingNodeId)).isEqualTo("startId");
    assertThat(updateDoc.get(PlanExecutionSummaryKeys.modules))
        .isEqualTo(new LinkedHashSet<>(List.of("pms", "common")));
    assertThat(updateDoc.get(PlanExecutionSummaryKeys.layoutNodeMap)).isNotNull();
    assertThat((Map<String, GraphLayoutNodeDTO>) updateDoc.get(PlanExecutionSummaryKeys.layoutNodeMap))
        .containsKeys("startId");
    assertThat(updateDoc.get(PlanExecutionSummaryKeys.executionInputConfigured))
        .isEqualTo(planExecutionMetadata.getExecutionInputConfigured());
    assertThat(updateDoc.get(PlanExecutionSummaryKeys.shouldUseSimplifiedLogBaseKey)).isEqualTo(false);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestOnQueue() {
    String inputSetYaml = "some-yaml";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(planExecutionId)
                                                      .inputSetYaml(inputSetYaml)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .executionInputConfigured(true)
                                                      .allowStagesExecution(true)
                                                      .notes("notes")
                                                      .build();
    StagesExecutionMetadata stagesExecutionMetadata = StagesExecutionMetadata.builder().isStagesExecution(true).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .stagesExecutionMetadata(stagesExecutionMetadata)
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .build();
    Map<String, String> setupAbstractions = new HashMap<>(ambiance.getSetupAbstractionsMap());
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(setupAbstractions)
                                      .metadata(ExecutionMetadata.newBuilder()
                                                    .setRunSequence(1)
                                                    .setPipelineIdentifier("pipelineId")
                                                    .setExecutionMode(ExecutionMode.NORMAL)
                                                    .build())
                                      .stagesExecutionMetadata(stagesExecutionMetadata)
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    ArgumentCaptor<ExecutionSummaryInfo> executionSummaryInfoArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionSummaryInfo.class);

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    doNothing()
        .when(pmsPipelineService)
        .saveExecutionInfo(any(), anyString(), executionSummaryInfoArgumentCaptor.capture(), anyBoolean());

    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());

    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onQueue(OrchestrationQueueInfo.builder()
                                                   .planExecution(planExecution)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    ExecutionSummaryInfo executionSummaryInfo = executionSummaryInfoArgumentCaptor.getValue();
    assertThat(executionSummaryInfo.getLastExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(executionSummaryInfo.getNumOfErrors()).isEmpty();
    assertThat(executionSummaryInfo.getDeployments()).isNotEmpty();
    assertThat(executionSummaryInfo.getDeployments().get(getFormattedDate())).isEqualTo(1);
    assertThat(executionSummaryInfo.getLastExecutionId()).isEqualTo(ambiance.getPlanExecutionId());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity.getNotesExistForPlanExecutionId()).isTrue();
    assertThat(capturedEntity).isNotNull();
    assertThat(capturedEntity.getRunSequence()).isEqualTo(1);
    assertThat(capturedEntity.getPipelineIdentifier()).isEqualTo("pipelineId");
    assertThat(capturedEntity.getPlanExecutionId()).isEqualTo(ambiance.getPlanExecutionId());
    assertThat(capturedEntity.getPipelineDeleted()).isFalse();
    assertThat(capturedEntity.getInternalStatus()).isEqualTo(null);
    assertThat(capturedEntity.getStatus()).isEqualTo(ExecutionStatus.NOTSTARTED);
    assertThat(capturedEntity.getTags()).isEmpty();
    assertThat(capturedEntity.getStartingNodeId()).isNull();
    assertThat(capturedEntity.getModules()).isEqualTo(new ArrayList<>());
    assertThat(capturedEntity.getLayoutNodeMap()).isEmpty();
    assertThat(capturedEntity.getStagesExecutionMetadata().isStagesExecution()).isTrue();
    assertThat(capturedEntity.isStagesExecutionAllowed()).isTrue();
    assertThat(capturedEntity.getExecutionInputConfigured()).isNull();
    assertThat(capturedEntity.getStoreType()).isNull();
    assertThat(capturedEntity.getConnectorRef()).isNull();
    assertThat(capturedEntity.getExecutionMode()).isEqualTo(ExecutionMode.NORMAL);
    assertThat(capturedEntity.getRollbackModeExecutionId()).isNull();

    verify(pmsPipelineService, times(1))
        .getPipeline(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, PIPELINE_IDENTIFIER, false, true, false,
            false,
            ScopeInfo.builder()
                .accountIdentifier(ACCOUNT_IDENTIFIER)
                .orgIdentifier(ORG_IDENTIFIER)
                .projectIdentifier(PROJECT_IDENTIFIER)
                .uniqueId("unique-id")
                .build(),
            true);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestOnStartWithNullPipelineYamlInPlanExecutionMetadata() {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(ambiance.getPlanExecutionId())
                                                      .inputSetYaml("some-yaml")
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .executionInputConfigured(true)
                                                      .allowStagesExecution(true)
                                                      .notes("notes")
                                                      .build();
    StagesExecutionMetadata stagesExecutionMetadata = StagesExecutionMetadata.builder().isStagesExecution(true).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .stagesExecutionMetadata(stagesExecutionMetadata)
            .build();
    Map<String, String> setupAbstractions = new HashMap<>(ambiance.getSetupAbstractionsMap());
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(setupAbstractions)
                                      .metadata(ExecutionMetadata.newBuilder()
                                                    .setRunSequence(1)
                                                    .setPipelineIdentifier("pipelineId")
                                                    .setExecutionMode(ExecutionMode.NORMAL)
                                                    .build())
                                      .stagesExecutionMetadata(stagesExecutionMetadata)
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    ArgumentCaptor<ExecutionSummaryInfo> executionSummaryInfoArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionSummaryInfo.class);

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(planService.fetchPlan(planId))
        .thenReturn(Plan.builder()
                        .graphLayoutInfo(GraphLayoutInfo.newBuilder()
                                             .setStartingNodeId("startId")
                                             .putLayoutNodes("startId",
                                                 GraphLayoutNode.newBuilder().setNodeGroup("node-group").build())
                                             .build())
                        .build());

    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    doNothing()
        .when(pmsPipelineService)
        .saveExecutionInfo(any(), anyString(), executionSummaryInfoArgumentCaptor.capture(), anyBoolean());

    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());

    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    ExecutionSummaryInfo executionSummaryInfo = executionSummaryInfoArgumentCaptor.getValue();
    assertThat(executionSummaryInfo.getLastExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(executionSummaryInfo.getNumOfErrors()).isEmpty();
    assertThat(executionSummaryInfo.getDeployments()).isNotEmpty();
    assertThat(executionSummaryInfo.getDeployments().get(getFormattedDate())).isEqualTo(1);
    assertThat(executionSummaryInfo.getLastExecutionId()).isEqualTo(ambiance.getPlanExecutionId());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity.getNotesExistForPlanExecutionId()).isTrue();
    assertThat(capturedEntity).isNotNull();
    assertThat(capturedEntity.getRunSequence()).isEqualTo(1);
    assertThat(capturedEntity.getPipelineIdentifier()).isEqualTo("pipelineId");
    assertThat(capturedEntity.getPlanExecutionId()).isEqualTo(ambiance.getPlanExecutionId());
    assertThat(capturedEntity.getPipelineDeleted()).isFalse();
    assertThat(capturedEntity.getInternalStatus()).isEqualTo(null);
    assertThat(capturedEntity.getStatus()).isEqualTo(ExecutionStatus.NOTSTARTED);
    assertThat(capturedEntity.getTags()).isEmpty();
    assertThat(capturedEntity.getStartingNodeId()).isEqualTo("startId");
    assertThat(capturedEntity.getModules()).containsExactly("pms", "common");
    assertThat(capturedEntity.getLayoutNodeMap()).isNotEmpty();
    assertThat(capturedEntity.getLayoutNodeMap()).containsKeys("startId");
    assertThat(capturedEntity.getStagesExecutionMetadata().isStagesExecution()).isTrue();
    assertThat(capturedEntity.isStagesExecutionAllowed()).isTrue();
    assertThat(capturedEntity.getExecutionInputConfigured())
        .isEqualTo(planExecutionMetadata.getExecutionInputConfigured());
    assertThat(capturedEntity.getStoreType()).isNull();
    assertThat(capturedEntity.getConnectorRef()).isNull();
    assertThat(capturedEntity.getExecutionMode()).isEqualTo(ExecutionMode.NORMAL);
    assertThat(capturedEntity.getRollbackModeExecutionId()).isNull();

    verify(pmsPipelineService, times(1))
        .getPipeline(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false,
            false,
            ScopeInfo.builder()
                .accountIdentifier(ACCOUNT_IDENTIFIER)
                .orgIdentifier(ORG_IDENTIFIER)
                .projectIdentifier(PROJECT_IDENTIFIER)
                .uniqueId("unique-id")
                .build(),
            true);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSaveExecutionGitMetadataWithNullEntityAndEntityGitDetails() {
    executionSummaryCreateEventHandler.saveExecutionGitMetadata(null, null);
    verify(gitMetadataService, times(0)).upsert(any(ScopeInfo.class), anyString(), anyString(), anyString());

    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder().entityGitDetails(null).build();

    executionSummaryCreateEventHandler.saveExecutionGitMetadata(entity, null);
    verify(gitMetadataService, times(0)).upsert(any(ScopeInfo.class), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSaveExecutionGitMetadataWithFeatureFlagEnabled() {
    when(pmsFeatureFlagService.isEnabled(any(), eq(DISABLE_PIPELINE_EXECUTION_GIT_METADATA_UPSERT))).thenReturn(true);
    PipelineExecutionSummaryEntity entity =
        PipelineExecutionSummaryEntity.builder()
            .entityGitDetails(EntityGitDetails.builder().repoName("test-repo").branch("main").build())
            .build();

    executionSummaryCreateEventHandler.saveExecutionGitMetadata(entity, null);
    verify(gitMetadataService, times(0)).upsert(any(ScopeInfo.class), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSaveExecutionGitMetadataWithNullRepoNameOrBranch() {
    when(pmsFeatureFlagService.isEnabled(any(), eq(DISABLE_PIPELINE_EXECUTION_GIT_METADATA_UPSERT))).thenReturn(false);

    PipelineExecutionSummaryEntity entity =
        PipelineExecutionSummaryEntity.builder()
            .entityGitDetails(EntityGitDetails.builder().repoName(null).branch("main").build())
            .build();

    executionSummaryCreateEventHandler.saveExecutionGitMetadata(entity, null);
    verify(gitMetadataService, times(0)).upsert(any(ScopeInfo.class), anyString(), anyString(), anyString());

    entity = PipelineExecutionSummaryEntity.builder()
                 .entityGitDetails(EntityGitDetails.builder().repoName("test-repo").branch(null).build())
                 .build();
    executionSummaryCreateEventHandler.saveExecutionGitMetadata(entity, null);
    verify(gitMetadataService, times(0)).upsert(any(ScopeInfo.class), anyString(), anyString(), anyString());

    entity = PipelineExecutionSummaryEntity.builder()
                 .entityGitDetails(EntityGitDetails.builder().repoName(null).branch(null).build())
                 .build();
    executionSummaryCreateEventHandler.saveExecutionGitMetadata(entity, null);
    verify(gitMetadataService, times(0)).upsert(any(ScopeInfo.class), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSaveExecutionGitMetadataSuccess() {
    when(pmsFeatureFlagService.isEnabled(any(), eq(DISABLE_PIPELINE_EXECUTION_GIT_METADATA_UPSERT))).thenReturn(false);

    String repoName = "testRepo";
    String branch = "main";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .uniqueId("unique-id")
                              .build();

    PipelineExecutionSummaryEntity entity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(ACCOUNT_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .entityGitDetails(EntityGitDetails.builder().repoName(repoName).branch(branch).build())
            .build();

    executionSummaryCreateEventHandler.saveExecutionGitMetadata(entity, scopeInfo);
    verify(gitMetadataService, times(1)).upsert(eq(scopeInfo), eq(PIPELINE_IDENTIFIER), eq(repoName), eq(branch));
  }

  private String getFormattedDate() {
    Date date = new Date();
    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
    return formatter.format(date);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void shouldTestInputSetBranchNameIsStoredInEntity() {
    String inputSetYaml = "some-yaml";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String inputSetBranchName = "feature-branch";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(ambiance.getPlanExecutionId())
                                                      .inputSetYaml(inputSetYaml)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .executionInputConfigured(true)
                                                      .allowStagesExecution(true)
                                                      .notes("notes")
                                                      .build();
    StagesExecutionMetadata stagesExecutionMetadata = StagesExecutionMetadata.builder().isStagesExecution(true).build();
    List<String> inputSetIdentifiers = Arrays.asList("inputset1", "inputset2");
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .stagesExecutionMetadata(stagesExecutionMetadata)
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .inputSetIdentifiers(inputSetIdentifiers)
            .inputSetBranchName(inputSetBranchName)
            .build();
    Map<String, String> setupAbstractions = new HashMap<>(ambiance.getSetupAbstractionsMap());
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(setupAbstractions)
                                      .metadata(ExecutionMetadata.newBuilder()
                                                    .setRunSequence(1)
                                                    .setPipelineIdentifier("pipelineId")
                                                    .setExecutionMode(ExecutionMode.NORMAL)
                                                    .build())
                                      .stagesExecutionMetadata(stagesExecutionMetadata)
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(planService.fetchPlan(planId))
        .thenReturn(Plan.builder()
                        .graphLayoutInfo(GraphLayoutInfo.newBuilder()
                                             .setStartingNodeId("startId")
                                             .putLayoutNodes("startId",
                                                 GraphLayoutNode.newBuilder().setNodeGroup("node-group").build())
                                             .build())
                        .build());

    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());

    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity).isNotNull();
    assertThat(capturedEntity.getInputSetIdentifiers()).isNotNull();
    assertThat(capturedEntity.getInputSetIdentifiers()).hasSize(2);
    assertThat(capturedEntity.getInputSetIdentifiers()).containsExactlyElementsOf(inputSetIdentifiers);
    assertThat(capturedEntity.getInputSetBranchName()).isEqualTo(inputSetBranchName);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void shouldTestInputSetBranchNameIsNullWhenNotProvided() {
    String inputSetYaml = "some-yaml";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(ambiance.getPlanExecutionId())
                                                      .inputSetYaml(inputSetYaml)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .executionInputConfigured(true)
                                                      .allowStagesExecution(true)
                                                      .notes("notes")
                                                      .build();
    StagesExecutionMetadata stagesExecutionMetadata = StagesExecutionMetadata.builder().isStagesExecution(true).build();
    List<String> inputSetIdentifiers = Arrays.asList("inputset1", "inputset2");
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .stagesExecutionMetadata(stagesExecutionMetadata)
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .inputSetIdentifiers(inputSetIdentifiers)
            .inputSetBranchName(null)
            .build();
    Map<String, String> setupAbstractions = new HashMap<>(ambiance.getSetupAbstractionsMap());
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(setupAbstractions)
                                      .metadata(ExecutionMetadata.newBuilder()
                                                    .setRunSequence(1)
                                                    .setPipelineIdentifier("pipelineId")
                                                    .setExecutionMode(ExecutionMode.NORMAL)
                                                    .build())
                                      .stagesExecutionMetadata(stagesExecutionMetadata)
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(planService.fetchPlan(planId))
        .thenReturn(Plan.builder()
                        .graphLayoutInfo(GraphLayoutInfo.newBuilder()
                                             .setStartingNodeId("startId")
                                             .putLayoutNodes("startId",
                                                 GraphLayoutNode.newBuilder().setNodeGroup("node-group").build())
                                             .build())
                        .build());

    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());

    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity).isNotNull();
    assertThat(capturedEntity.getInputSetIdentifiers()).isNotNull();
    assertThat(capturedEntity.getInputSetIdentifiers()).hasSize(2);
    assertThat(capturedEntity.getInputSetBranchName()).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldFocusDagPostExecutionRollbackLayoutOnRollbackTarget() {
    String rollbackStageId = "stageS2";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                             .setEnableDAG(true)
                                             .setRunSequence(1)
                                             .setPipelineIdentifier("pipelineId")
                                             .build())
                            .build();

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(planExecutionId)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .postExecutionRollbackInfos(Collections.singletonList(
                PostExecutionRollbackInfo.newBuilder().setPostExecutionRollbackStageId(rollbackStageId).build()))
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .build();

    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(new HashMap<>(ambiance.getSetupAbstractionsMap()))
                                      .metadata(ambiance.getMetadata())
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(rollbackStageId)
                            .setDependencies(StringArray.newBuilder().addValues("stageS1").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageS1")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(pmsFeatureFlagService.isEnabled(any(), eq(PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);
    when(planService.fetchPlan(planId))
        .thenReturn(
            Plan.builder()
                .graphLayoutInfo(
                    GraphLayoutInfo.newBuilder()
                        .setIsDagEnabled(true)
                        .setStartingNodeId("stageS1")
                        .addStartingNodeIds("stageS1")
                        .setDependencyGraph(dependencyGraph)
                        .putLayoutNodes("stageS1", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .putLayoutNodes(rollbackStageId, GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .build())
                .build());
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    doNothing().when(pmsPipelineService).saveExecutionInfo(any(), anyString(), any(), anyBoolean());
    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());
    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity.getIsDagEnabled()).isFalse();
    assertThat(capturedEntity.getDependencyGraph()).isNull();
    assertThat(capturedEntity.getStartingNodeId()).isEqualTo(rollbackStageId);
    assertThat(capturedEntity.getStartingNodeIds()).containsExactly(rollbackStageId);
    assertThat(capturedEntity.getLayoutNodeMap().keySet()).containsExactly(rollbackStageId);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldPruneDagLayoutForPostExecutionRollbackInstanceOnParallelBranch() {
    String rollbackStageId = "stageS2";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                             .setEnableDAG(true)
                                             .setRunSequence(1)
                                             .setPipelineIdentifier("pipelineId")
                                             .build())
                            .build();

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(planExecutionId)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .postExecutionRollbackInfos(Collections.singletonList(
                PostExecutionRollbackInfo.newBuilder().setPostExecutionRollbackStageId(rollbackStageId).build()))
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .build();

    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(new HashMap<>(ambiance.getSetupAbstractionsMap()))
                                      .metadata(ambiance.getMetadata())
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    // Reversed fan-out DAG: S4 -> S3 -> (S1 || S2). Instance rollback on S2.
    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageS4")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageS3")
                            .setDependencies(StringArray.newBuilder().addValues("stageS4").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageS1")
                            .setDependencies(StringArray.newBuilder().addValues("stageS3").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(rollbackStageId)
                            .setDependencies(StringArray.newBuilder().addValues("stageS3").build())
                            .build())
            .build();

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(pmsFeatureFlagService.isEnabled(any(), eq(PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);
    when(planService.fetchPlan(planId))
        .thenReturn(
            Plan.builder()
                .graphLayoutInfo(
                    GraphLayoutInfo.newBuilder()
                        .setIsDagEnabled(true)
                        .setStartingNodeId("stageS4")
                        .addStartingNodeIds("stageS4")
                        .setDependencyGraph(dependencyGraph)
                        .putLayoutNodes("stageS1", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .putLayoutNodes("stageS3", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .putLayoutNodes("stageS4", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .putLayoutNodes(rollbackStageId, GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .build())
                .build());
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    doNothing().when(pmsPipelineService).saveExecutionInfo(any(), anyString(), any(), anyBoolean());
    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());
    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity.getIsDagEnabled()).isFalse();
    assertThat(capturedEntity.getStartingNodeId()).isEqualTo(rollbackStageId);
    assertThat(capturedEntity.getStartingNodeIds()).containsExactly(rollbackStageId);
    assertThat(capturedEntity.getDependencyGraph()).isNull();
    assertThat(capturedEntity.getLayoutNodeMap().keySet()).containsExactly(rollbackStageId);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldRetainRollbackTargetInternalLayoutSubgraphForDagPostExecutionRollback() {
    String rollbackStageId = "stageDeploy";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                             .setEnableDAG(true)
                                             .setRunSequence(1)
                                             .setPipelineIdentifier("pipelineId")
                                             .build())
                            .build();

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(planExecutionId)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .postExecutionRollbackInfos(Collections.singletonList(
                PostExecutionRollbackInfo.newBuilder().setPostExecutionRollbackStageId(rollbackStageId).build()))
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .build();

    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(new HashMap<>(ambiance.getSetupAbstractionsMap()))
                                      .metadata(ambiance.getMetadata())
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(rollbackStageId)
                            .setDependencies(StringArray.newBuilder().addValues("stageS2").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageS2")
                            .setDependencies(StringArray.newBuilder().addValues("stageCustom1").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageCustom1")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(pmsFeatureFlagService.isEnabled(any(), eq(PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);
    when(planService.fetchPlan(planId))
        .thenReturn(
            Plan.builder()
                .graphLayoutInfo(
                    GraphLayoutInfo.newBuilder()
                        .setIsDagEnabled(true)
                        .setStartingNodeId("stageCustom1")
                        .addStartingNodeIds("stageCustom1")
                        .setDependencyGraph(dependencyGraph)
                        .putLayoutNodes("stageCustom1",
                            GraphLayoutNode.newBuilder()
                                .setNodeGroup("STAGE")
                                .setEdgeLayoutList(
                                    EdgeLayoutList.newBuilder().addCurrentNodeChildren("custom1Steps").build())
                                .build())
                        .putLayoutNodes("custom1Steps", GraphLayoutNode.newBuilder().setNodeGroup("STEPS").build())
                        .putLayoutNodes("stageS2", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .putLayoutNodes(rollbackStageId,
                            GraphLayoutNode.newBuilder()
                                .setNodeGroup("STAGE")
                                .setEdgeLayoutList(
                                    EdgeLayoutList.newBuilder().addCurrentNodeChildren("combinedRollback").build())
                                .build())
                        .putLayoutNodes("combinedRollback",
                            GraphLayoutNode.newBuilder()
                                .setNodeGroup("STEP")
                                .setEdgeLayoutList(
                                    EdgeLayoutList.newBuilder().addCurrentNodeChildren("rollbackSteps").build())
                                .build())
                        .putLayoutNodes("rollbackSteps", GraphLayoutNode.newBuilder().setNodeGroup("STEP").build())
                        .putLayoutNodes("strategyDummy", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .build())
                .build());
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    doNothing().when(pmsPipelineService).saveExecutionInfo(any(), anyString(), any(), anyBoolean());
    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());
    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity.getLayoutNodeMap().keySet())
        .containsExactlyInAnyOrder(rollbackStageId, "combinedRollback", "rollbackSteps");
    assertThat(capturedEntity.getLayoutNodeMap())
        .doesNotContainKeys("custom1Steps", "strategyDummy", "stageS2", "stageCustom1");
    assertThat(capturedEntity.getStartingNodeId()).isEqualTo(rollbackStageId);
    assertThat(capturedEntity.getStartingNodeIds()).containsExactly(rollbackStageId);
    assertThat(capturedEntity.getDependencyGraph()).isNull();
    assertThat(capturedEntity.getIsDagEnabled()).isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldUseSequentialLayoutWhenDagFeatureFlagDisabledForPostExecutionRollback() {
    String rollbackStageId = "stageS2";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                             .setEnableDAG(true)
                                             .setRunSequence(1)
                                             .setPipelineIdentifier("pipelineId")
                                             .build())
                            .build();

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(planExecutionId)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .postExecutionRollbackInfos(Collections.singletonList(
                PostExecutionRollbackInfo.newBuilder().setPostExecutionRollbackStageId(rollbackStageId).build()))
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .build();

    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(new HashMap<>(ambiance.getSetupAbstractionsMap()))
                                      .metadata(ambiance.getMetadata())
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(rollbackStageId)
                            .setDependencies(StringArray.newBuilder().addValues("stageS1").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageS1")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(pmsFeatureFlagService.isEnabled(any(), eq(PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(false);
    when(planService.fetchPlan(planId))
        .thenReturn(
            Plan.builder()
                .graphLayoutInfo(
                    GraphLayoutInfo.newBuilder()
                        .setIsDagEnabled(true)
                        .setStartingNodeId("stageS1")
                        .addStartingNodeIds("stageS1")
                        .setDependencyGraph(dependencyGraph)
                        .putLayoutNodes("stageS1", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .putLayoutNodes(rollbackStageId, GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .build())
                .build());
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    doNothing().when(pmsPipelineService).saveExecutionInfo(any(), anyString(), any(), anyBoolean());
    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());
    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity.getIsDagEnabled()).isTrue();
    assertThat(capturedEntity.getDependencyGraph()).isNull();
    assertThat(capturedEntity.getStartingNodeId()).isEqualTo(rollbackStageId);
    assertThat(capturedEntity.getLayoutNodeMap()).containsOnlyKeys(rollbackStageId);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldUseSequentialLayoutWhenPlanNotDagEnabledForPostExecutionRollback() {
    String rollbackStageId = "stageS2";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                             .setEnableDAG(true)
                                             .setRunSequence(1)
                                             .setPipelineIdentifier("pipelineId")
                                             .build())
                            .build();

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(planExecutionId)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .postExecutionRollbackInfos(Collections.singletonList(
                PostExecutionRollbackInfo.newBuilder().setPostExecutionRollbackStageId(rollbackStageId).build()))
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .build();

    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(new HashMap<>(ambiance.getSetupAbstractionsMap()))
                                      .metadata(ambiance.getMetadata())
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(rollbackStageId)
                            .setDependencies(StringArray.newBuilder().addValues("stageS1").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageS1")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(pmsFeatureFlagService.isEnabled(any(), eq(PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);
    when(planService.fetchPlan(planId))
        .thenReturn(
            Plan.builder()
                .graphLayoutInfo(
                    GraphLayoutInfo.newBuilder()
                        .setIsDagEnabled(false)
                        .setStartingNodeId("stageS1")
                        .addStartingNodeIds("stageS1")
                        .setDependencyGraph(dependencyGraph)
                        .putLayoutNodes("stageS1", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .putLayoutNodes(rollbackStageId, GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .build())
                .build());
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    doNothing().when(pmsPipelineService).saveExecutionInfo(any(), anyString(), any(), anyBoolean());
    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());
    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity.getIsDagEnabled()).isFalse();
    assertThat(capturedEntity.getDependencyGraph()).isNull();
    assertThat(capturedEntity.getStartingNodeId()).isEqualTo(rollbackStageId);
    assertThat(capturedEntity.getStartingNodeIds()).containsExactly(rollbackStageId);
    assertThat(capturedEntity.getLayoutNodeMap()).containsOnlyKeys(rollbackStageId);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldExcludeSiblingDeployStagesWhenDagPostExecutionRollbackTargetsDeploy2Only() {
    String rollbackStageId = "stageDeploy2";
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                             .setEnableDAG(true)
                                             .setRunSequence(1)
                                             .setPipelineIdentifier("pipelineId")
                                             .build())
                            .build();

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .planExecutionId(planExecutionId)
                                                      .yaml("pipeline :\n  identifier: pipelineId")
                                                      .build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .postExecutionRollbackInfos(Collections.singletonList(
                PostExecutionRollbackInfo.newBuilder().setPostExecutionRollbackStageId(rollbackStageId).build()))
            .pipelineYaml("pipeline :\n  identifier: pipelineId")
            .build();

    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .ambiance(ambiance)
                                      .setupAbstractions(new HashMap<>(ambiance.getSetupAbstractionsMap()))
                                      .metadata(ambiance.getMetadata())
                                      .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .yaml("pipeline :\n  identifier: pipelineId")
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(rollbackStageId)
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageDeploy")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageS2")
                            .setDependencies(
                                StringArray.newBuilder().addValues("stageDeploy").addValues(rollbackStageId).build())
                            .build())
            .build();

    ArgumentCaptor<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineExecutionSummaryEntity.class);

    when(planExecutionService.get(planExecutionId)).thenReturn(planExecution);
    when(pmsFeatureFlagService.isEnabled(any(), eq(PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);
    when(planService.fetchPlan(planId))
        .thenReturn(
            Plan.builder()
                .graphLayoutInfo(
                    GraphLayoutInfo.newBuilder()
                        .setIsDagEnabled(true)
                        .setStartingNodeId(rollbackStageId)
                        .addStartingNodeIds(rollbackStageId)
                        .setDependencyGraph(dependencyGraph)
                        .putLayoutNodes(rollbackStageId,
                            GraphLayoutNode.newBuilder()
                                .setNodeGroup("STAGE")
                                .setEdgeLayoutList(EdgeLayoutList.newBuilder()
                                                       .addCurrentNodeChildren("deploy2Rollback")
                                                       .addNextIds("stageDeploy")
                                                       .build())
                                .build())
                        .putLayoutNodes("deploy2Rollback", GraphLayoutNode.newBuilder().setNodeGroup("STEP").build())
                        .putLayoutNodes("stageDeploy",
                            GraphLayoutNode.newBuilder()
                                .setNodeGroup("STAGE")
                                .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("stageS2").build())
                                .build())
                        .putLayoutNodes("stageS2", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build())
                        .build())
                .build());
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    doNothing().when(pmsPipelineService).saveExecutionInfo(any(), anyString(), any(), anyBoolean());
    doReturn(null).when(pmsExecutionSummaryService).save(pipelineExecutionSummaryEntityArgumentCaptor.capture());
    when(nodeTypeLookupService.findNodeTypeServiceName(anyString())).thenReturn("pms");

    executionSummaryCreateEventHandler.onStart(OrchestrationStartInfo.builder()
                                                   .ambiance(ambiance)
                                                   .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                   .build());

    PipelineExecutionSummaryEntity capturedEntity = pipelineExecutionSummaryEntityArgumentCaptor.getValue();
    assertThat(capturedEntity.getLayoutNodeMap().keySet())
        .containsExactlyInAnyOrder(rollbackStageId, "deploy2Rollback");
    assertThat(capturedEntity.getLayoutNodeMap()).doesNotContainKeys("stageDeploy", "stageS2");
    assertThat(capturedEntity.getStartingNodeId()).isEqualTo(rollbackStageId);
    assertThat(capturedEntity.getIsDagEnabled()).isFalse();
  }
}
