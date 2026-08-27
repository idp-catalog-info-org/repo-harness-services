/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SHIVAM;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static reactor.core.publisher.Mono.when;

import io.harness.CategoryTest;
import io.harness.PipelineServiceTestHelper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.engine.executions.gitmetadata.service.PipelineExecutionGitMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.service.InputFileService;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity;
import io.harness.pms.pipelinedelete.service.PipelineDeleteProcessorIteratorEntityService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.preflight.service.intfc.PreflightService;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.search.service.PipelineSearchService;
import io.harness.service.GraphGenerationService;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.waiter.WaitNotifyEngine;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class PipelineEntityCRUDStreamListenerTest extends CategoryTest {
  @Mock private NGTriggerService ngTriggerService;
  @Mock private NGTriggerEventsService ngTriggerEventsService;
  @Mock private PipelineMetadataService pipelineMetadataService;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock private BarrierService barrierService;
  @Mock private PreflightService preflightService;
  @Mock private PmsSweepingOutputService pmsSweepingOutputService;
  @Mock private PmsOutcomeService pmsOutcomeService;
  @Mock private InterruptService interruptService;
  @Mock private InputFileService inputFileService;
  @Mock private GraphGenerationService graphGenerationService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PlanExpansionService planExpansionService;

  @Mock private NGSettingsClient ngSettingsClient;

  @Mock private ThreadPoolExecutor pipelineExecutorService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PMSExecutionService pmsExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PipelineDeleteProcessorIteratorEntityService deleteProcessorIteratorEntityService;
  @Mock private PipelineSearchService pipelineSearchService;
  @Mock private ExecutionRetentionService executionRetentionService;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @Mock private PipelineExecutionGitMetadataService pipelineExecutionGitMetadataService;
  @Mock private MetricService metricService;

  private PipelineEntityCRUDStreamListener pipelineEntityCRUDStreamListener;

  @Before
  public void setUp() throws ExecutionException, InterruptedException {
    MockitoAnnotations.initMocks(this);
    pipelineEntityCRUDStreamListener = Mockito.spy(new PipelineEntityCRUDStreamListener(ngTriggerService,
        pipelineMetadataService, pmsExecutionSummaryService, barrierService, preflightService, pmsSweepingOutputService,
        pmsOutcomeService, interruptService, inputFileService, graphGenerationService, nodeExecutionService,
        ngTriggerEventsService, planExecutionService, planExpansionService, ngSettingsClient, pipelineExecutorService,
        200, pmsFeatureFlagService, pmsExecutionService, waitNotifyEngine, deleteProcessorIteratorEntityService,
        executionRetentionService, pipelineSearchService, scopeResolutionHelper, pipelineExecutionGitMetadataService,
        metricService));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testEmptyHandleMessage() {
    Message message = Message.newBuilder().build();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNonPipelineEntityEventHandleMessage() {
    // Action type is not delete and even entity type is not pipeline
    Message message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, USER_ENTITY)
                            .putMetadata(ACTION, CREATE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
    // Zero interaction with any one of pipeline metadata delete
    verify(ngTriggerService, times(0)).deleteAllForPipeline(any(), any(), any(), any(), any());
    verify(pipelineExecutionGitMetadataService, times(0))
        .deletePipelineGitMetadata(any(), any(), any(), any(), anyBoolean(), any());

    // Zero interaction with any one of pipeline execution delete
    verify(pmsExecutionSummaryService, times(0))
        .fetchPlanExecutionIdsAndStatusFromAnalytics(any(), any(), any(), any(), any());

    // Action type is not delete but entity is pipeline
    message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                            .putMetadata(ACTION, CREATE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
    // Zero interaction with any one of pipeline metadata delete
    verify(ngTriggerService, times(0)).deleteAllForPipeline(any(), any(), any(), any(), any());
    verify(pipelineExecutionGitMetadataService, times(0))
        .deletePipelineGitMetadata(any(), any(), any(), any(), anyBoolean(), any());

    // Zero interaction with any one of pipeline execution delete
    verify(pmsExecutionSummaryService, times(0))
        .fetchPlanExecutionIdsAndStatusFromAnalytics(any(), any(), any(), any(), any());

    // Data is not parsable into EntityChangeDTO
    message = Message.newBuilder()
                  .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                  .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                                  .putMetadata(ACTION, CREATE_ACTION)
                                  .setData(ByteString.copyFromUtf8("Dummy"))
                                  .build())
                  .build();
    Message finalMessage = message;
    assertThatCode(() -> pipelineEntityCRUDStreamListener.handleMessage(finalMessage))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testHandleMessageWithZeroExecutions() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    Stream<Object> executionsIterator =
        PipelineServiceTestHelper.createCloseableIterator(Collections.emptyIterator()).stream();
    doReturn(executionsIterator)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsAndStatusFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null);

    doNothing()
        .when(pipelineEntityCRUDStreamListener)
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());

    assertTrue(pipelineEntityCRUDStreamListener.processDeleteEvent(
        Instant.now(), Duration.ofHours(1), ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, false, null));

    // Verify pipeline metadata delete
    verify(ngTriggerService, times(1)).deleteAllForPipeline(any(), any(), any(), any(), any());
    verify(pipelineMetadataService, times(1)).deletePipelineMetadata(any(), any(), any(), any(), any());
    verify(preflightService, times(1)).deleteAllPreflightEntityForGivenPipeline(any(), any(), any(), any(), any());
    verify(pipelineExecutionGitMetadataService, times(1))
        .deletePipelineGitMetadata(any(), any(), any(), any(), anyBoolean(), any());

    // Execution ids call only once as empty list
    verify(pmsExecutionSummaryService, times(1))
        .fetchPlanExecutionIdsAndStatusFromAnalytics(any(), any(), any(), any(), any());
    // Verify execution delete calls
    verify(pipelineEntityCRUDStreamListener, times(0))
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testHandleMessageWithExecutionsMoreThanBatchSize() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    List<PipelineExecutionSummaryEntity> executionIds = new LinkedList<>();
    for (int i = 0; i < 505; i++) {
      PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder()
                                                  .planExecutionId(String.valueOf(i))
                                                  .status(ExecutionStatus.SUCCESS)
                                                  .build();
      executionIds.add(entity);
    }

    Stream<PipelineExecutionSummaryEntity> executionsIterator =
        PipelineServiceTestHelper.createCloseableIterator(executionIds.iterator()).stream();
    doReturn(executionsIterator)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsAndStatusFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null);

    doNothing()
        .when(pipelineEntityCRUDStreamListener)
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());
    assertTrue(pipelineEntityCRUDStreamListener.processDeleteEvent(
        Instant.now(), Duration.ofHours(1), ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, false, null));

    // Verify pipeline metadata delete as its called only once
    verify(ngTriggerService, times(1)).deleteAllForPipeline(any(), any(), any(), any(), any());
    verify(pipelineMetadataService, times(1)).deletePipelineMetadata(any(), any(), any(), any(), any());
    verify(preflightService, times(1)).deleteAllPreflightEntityForGivenPipeline(any(), any(), any(), any(), any());
    verify(pipelineExecutionGitMetadataService, times(1))
        .deletePipelineGitMetadata(any(), any(), any(), any(), anyBoolean(), any());

    // Execution ids call only once as empty list
    verify(pmsExecutionSummaryService, times(1))
        .fetchPlanExecutionIdsAndStatusFromAnalytics(any(), any(), any(), any(), any());

    verify(pipelineEntityCRUDStreamListener, times(3))
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeletePipelineExecutionsDetailsInternal() {
    Reflect.on(pipelineEntityCRUDStreamListener).set("pipelineExecutorService", Executors.newFixedThreadPool(1));

    doNothing().when(barrierService).deleteAllForGivenPlanExecutionId(any());
    doNothing().when(pmsSweepingOutputService).deleteAllSweepingOutputInstances(any());
    doNothing().when(pmsOutcomeService).deleteAllOutcomesInstances(any());
    doNothing().when(interruptService).deleteAllInterrupts(any());
    doNothing().when(graphGenerationService).deleteAllGraphMetadataForGivenExecutionIds(any(), anyBoolean(), any());
    doNothing().when(nodeExecutionService).deleteAllNodeExecutionAndMetadata(any());
    doNothing().when(planExecutionService).deleteAllPlanExecutionAndMetadata(any(), anyBoolean(), any());
    doNothing().when(planExpansionService).deleteAllExpansions(any());
    doNothing().when(executionRetentionService).deleteAllPlanExecutionsData(any(), anyBoolean());
    doNothing().when(inputFileService).deleteFilesForAllExecutions(any(), anyBoolean());
    doNothing().when(pmsExecutionSummaryService).deleteAllSummaryForGivenPlanExecutionIds(any(), anyBoolean(), any());

    pipelineEntityCRUDStreamListener.deletePipelineExecutionsDetailsInternal(
        Collections.singleton("uuid1"), false, "abc");

    // Verify execution delete calls
    verify(barrierService, times(1)).deleteAllForGivenPlanExecutionId(any());
    // Verify Delete sweepingOutput
    verify(pmsSweepingOutputService, times(1)).deleteAllSweepingOutputInstances(any());
    // Verify Delete outcome instances
    verify(pmsOutcomeService, times(1)).deleteAllOutcomesInstances(any());
    // Verify Delete all interrupts
    verify(interruptService, times(1)).deleteAllInterrupts(any());
    // Verify graph metadata delete
    verify(graphGenerationService, times(1)).deleteAllGraphMetadataForGivenExecutionIds(any(), anyBoolean(), any());
    // Verify nodeExecutions and its metadata delete
    verify(nodeExecutionService, times(1)).deleteAllNodeExecutionAndMetadata(any());
    // Verify planExecutions and its metadata delete
    verify(planExecutionService, times(1)).deleteAllPlanExecutionAndMetadata(any(), anyBoolean(), any());
    // Verify planExpansion delete
    verify(planExpansionService, times(1)).deleteAllExpansions(any());
    // Verify retention data delete
    verify(executionRetentionService, times(1)).deleteAllPlanExecutionsData(any(), anyBoolean());
    // Verify runtimeFileInput delete
    verify(inputFileService, times(1)).deleteFilesForAllExecutions(any(), anyBoolean());
    // Verify summary delete
    verify(pmsExecutionSummaryService, times(1)).deleteAllSummaryForGivenPlanExecutionIds(any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleMessage() throws IOException {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(EntityChangeDTO.newBuilder()
                                                       .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                       .setOrgIdentifier(StringValue.of(ORG_ID))
                                                       .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                                       .setIdentifier(StringValue.of(PIPELINE_ID))
                                                       .build()
                                                       .toByteString())
                                          .build())
                          .build();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
    verify(deleteProcessorIteratorEntityService, times(1))
        .save(eq(PipelineDeleteProcessorIteratorEntity.builder()
                     .accountIdentifier(ACCOUNT_ID)
                     .orgIdentifier(ORG_ID)
                     .pipelineIdentifier(PIPELINE_ID)
                     .projectIdentifier(PROJECT_ID)
                     .retainPipelineExecutionDetailsAfterDelete(false)
                     .parentUniqueId("")
                     .nextIteration(0L)
                     .build()));
    Call<ResponseDTO<SettingValueResponseDTO>> settingCall = mock(Call.class);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(settingCall)
        .when(ngSettingsClient)
        .getSetting(eq(NGPipelineSettingsConstant.DO_NOT_DELETE_PIPELINE_EXECUTION_DETAILS.getName()), eq(ACCOUNT_ID),
            eq(null), eq(null));
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(settingCall).execute();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
    verify(deleteProcessorIteratorEntityService, times(1))
        .save(eq(PipelineDeleteProcessorIteratorEntity.builder()
                     .accountIdentifier(ACCOUNT_ID)
                     .orgIdentifier(ORG_ID)
                     .pipelineIdentifier(PIPELINE_ID)
                     .projectIdentifier(PROJECT_ID)
                     .parentUniqueId("")
                     .retainPipelineExecutionDetailsAfterDelete(true)
                     .nextIteration(0L)
                     .build()));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleMessageFalseSetting() throws IOException {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(EntityChangeDTO.newBuilder()
                                                       .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                       .setOrgIdentifier(StringValue.of(ORG_ID))
                                                       .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                                       .setIdentifier(StringValue.of(PIPELINE_ID))
                                                       .build()
                                                       .toByteString())
                                          .build())
                          .build();
    Call<ResponseDTO<SettingValueResponseDTO>> settingCall = mock(Call.class);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    doReturn(settingCall)
        .when(ngSettingsClient)
        .getSetting(eq(NGPipelineSettingsConstant.DO_NOT_DELETE_PIPELINE_EXECUTION_DETAILS.getName()), eq(ACCOUNT_ID),
            eq(null), eq(null));
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(settingCall).execute();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
    verify(deleteProcessorIteratorEntityService, times(1))
        .save(eq(PipelineDeleteProcessorIteratorEntity.builder()
                     .accountIdentifier(ACCOUNT_ID)
                     .orgIdentifier(ORG_ID)
                     .pipelineIdentifier(PIPELINE_ID)
                     .projectIdentifier(PROJECT_ID)
                     .parentUniqueId("")
                     .retainPipelineExecutionDetailsAfterDelete(false)
                     .nextIteration(0L)
                     .build()));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testHandleMessageWithExecutionsLessThanBatchSize() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    List<PipelineExecutionSummaryEntity> executionIds = new LinkedList<>();
    for (int i = 0; i < 40; i++) {
      PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder()
                                                  .planExecutionId(String.valueOf(i))
                                                  .status(ExecutionStatus.SUCCESS)
                                                  .build();
      executionIds.add(entity);
    }

    Stream<PipelineExecutionSummaryEntity> executionsIterator =
        PipelineServiceTestHelper.createCloseableIterator(executionIds.iterator()).stream();
    doReturn(executionsIterator)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsAndStatusFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null);
    doNothing()
        .when(pipelineEntityCRUDStreamListener)
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());

    assertTrue(pipelineEntityCRUDStreamListener.processDeleteEvent(
        Instant.now(), Duration.ofHours(1), ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, false, null));

    // Verify pipeline metadata delete as its called only once
    verify(ngTriggerService, times(1)).deleteAllForPipeline(any(), any(), any(), any(), any());
    verify(pipelineMetadataService, times(1)).deletePipelineMetadata(any(), any(), any(), any(), any());
    verify(preflightService, times(1)).deleteAllPreflightEntityForGivenPipeline(any(), any(), any(), any(), any());
    verify(pipelineExecutionGitMetadataService, times(1))
        .deletePipelineGitMetadata(any(), any(), any(), any(), anyBoolean(), any());

    // Execution ids call only once as empty list
    verify(pmsExecutionSummaryService, times(1))
        .fetchPlanExecutionIdsAndStatusFromAnalytics(any(), any(), any(), any(), any());

    verify(pipelineEntityCRUDStreamListener, times(1))
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testTriggerEventDeletionOnPipelineDeletion() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    List<PipelineExecutionSummaryEntity> executionIds = new LinkedList<>();
    for (int i = 0; i < 40; i++) {
      PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder()
                                                  .planExecutionId(String.valueOf(i))
                                                  .status(ExecutionStatus.SUCCESS)
                                                  .build();
      executionIds.add(entity);
    }

    Stream<PipelineExecutionSummaryEntity> executionsIterator =
        PipelineServiceTestHelper.createCloseableIterator(executionIds.iterator()).stream();
    doReturn(executionsIterator)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsAndStatusFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null);
    doNothing()
        .when(pipelineEntityCRUDStreamListener)
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());
    assertTrue(pipelineEntityCRUDStreamListener.processDeleteEvent(
        Instant.now(), Duration.ofHours(1), ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, false, null));

    // Verify Git metadata deletion is called
    verify(pipelineExecutionGitMetadataService)
        .deletePipelineGitMetadata(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, false, null);
    verify(ngTriggerEventsService, times(1)).deleteAllForPipeline(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testTriggerDisableEventDeletionOnPipelineDeletion() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";
    when(() -> NGRestUtils.getResponse(any())).thenReturn(SettingValueResponseDTO.builder().value("true").build());

    List<PipelineExecutionSummaryEntity> executionIds = new LinkedList<>();
    for (int i = 0; i < 40; i++) {
      PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder()
                                                  .planExecutionId(String.valueOf(i))
                                                  .status(ExecutionStatus.SUCCESS)
                                                  .build();
      executionIds.add(entity);
    }

    Stream<PipelineExecutionSummaryEntity> executionsIterator =
        PipelineServiceTestHelper.createCloseableIterator(executionIds.iterator()).stream();
    doReturn(executionsIterator)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsAndStatusFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null);
    doNothing()
        .when(pipelineEntityCRUDStreamListener)
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());
    assertTrue(pipelineEntityCRUDStreamListener.processDeleteEvent(
        Instant.now(), Duration.ofHours(1), ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, false, null));
    verify(ngTriggerEventsService, times(1)).deleteAllForPipeline(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testDeletePipelineExecutionsDetailsRecordsMetricsWithMongoDB() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    // Setup test data
    PipelineExecutionSummaryEntity execution1 = createPipelineExecutionSummaryEntity("plan1", ExecutionStatus.SUCCESS);
    PipelineExecutionSummaryEntity execution2 = createPipelineExecutionSummaryEntity("plan2", ExecutionStatus.RUNNING);
    PipelineExecutionSummaryEntity execution3 = createPipelineExecutionSummaryEntity("plan3", ExecutionStatus.FAILED);

    Stream<PipelineExecutionSummaryEntity> executionStream = Stream.of(execution1, execution2, execution3);

    // Mock feature flag disabled (MongoDB path)
    doReturn(false).when(pmsFeatureFlagService).isEnabled(anyString(), any(FeatureName.class));

    // Mock execution service to return test data
    doReturn(executionStream)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsAndStatusFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null);

    // Mock delete internal method
    doNothing()
        .when(pipelineEntityCRUDStreamListener)
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());

    // Execute the method
    boolean result = pipelineEntityCRUDStreamListener.processDeleteEvent(
        Instant.now(), Duration.ofHours(1), ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, false, null);

    // Verify result
    assertTrue(result);

    // Verify metrics were recorded
    verify(metricService, times(1)).recordMetric(eq("pipeline_delete_entities_processed_count"), eq(3.0));
    verify(metricService, times(1))
        .recordDuration(eq("pipeline_delete_entities_processing_duration"), any(Duration.class));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testDeletePipelineExecutionsDetailsRecordsCorrectEntityCount() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    // Setup test data with specific number of entities
    int expectedEntityCount = 5;
    List<PipelineExecutionSummaryEntity> executions = new LinkedList<>();
    for (int i = 0; i < expectedEntityCount; i++) {
      executions.add(createPipelineExecutionSummaryEntity("plan" + i, ExecutionStatus.SUCCESS));
    }

    Stream<PipelineExecutionSummaryEntity> executionStream = executions.stream();

    // Mock feature flag disabled (MongoDB path)
    doReturn(false).when(pmsFeatureFlagService).isEnabled(anyString(), any(FeatureName.class));

    // Mock execution service to return test data
    doReturn(executionStream)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsAndStatusFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null);

    // Mock delete internal method
    doNothing()
        .when(pipelineEntityCRUDStreamListener)
        .deletePipelineExecutionsDetailsInternal(any(), anyBoolean(), any());

    // Execute the method
    boolean result = pipelineEntityCRUDStreamListener.processDeleteEvent(
        Instant.now(), Duration.ofHours(1), ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, false, null);

    // Verify result
    assertTrue(result);

    // Verify correct entity count was recorded
    verify(metricService, times(1))
        .recordMetric(eq("pipeline_delete_entities_processed_count"), eq((double) expectedEntityCount));
    verify(metricService, times(1))
        .recordDuration(eq("pipeline_delete_entities_processing_duration"), any(Duration.class));
  }

  private PipelineExecutionSummaryEntity createPipelineExecutionSummaryEntity(
      String planExecutionId, ExecutionStatus status) {
    return PipelineExecutionSummaryEntity.builder().planExecutionId(planExecutionId).status(status).build();
  }
}