/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.iterator;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.goconvert.EntityType;
import io.harness.goconvert.GoConvertServiceClient;
import io.harness.goconvert.proto.ConvertResponse;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.conversion.beans.ConversionActionType;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.pms.conversion.beans.EntityIdentifierDTO;
import io.harness.pms.conversion.beans.EntityMetadata;
import io.harness.pms.conversion.service.ConversionJobService;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.repositories.conversion.ConversionChecksumRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class ConversionJobIteratorTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account123";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "project1";
  private static final String JOB_UUID = "job-uuid-1";
  private static final String PARENT_JOB_UUID = "parent-job-uuid";
  private static final String PIPELINE_ID = "myPipeline";
  private static final String V0_YAML = "pipeline:\n  name: test\n  identifier: myPipeline";
  private static final String V1_YAML = "version: 1\nkind: pipeline\nspec:\n  stages: []";
  private static final String CHECKSUM = "abc123";

  @Mock private MongoTemplate mongoTemplate;
  @Mock private PersistentLocker persistentLocker;
  @Mock private ConversionJobService conversionJobService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PMSPipelineService pmsPipelineService;
  @Mock private PMSInputSetService pmsInputSetService;
  @Mock private GoConvertServiceClient goConvertServiceClient;
  @Mock private ConversionChecksumRepository conversionChecksumRepository;
  @Mock private NGTriggerService ngTriggerService;
  @Mock private AcquiredLock<?> acquiredLock;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;

  @InjectMocks private ConversionJobIterator conversionJobIterator;

  private final ScopeInfo scopeInfo = ScopeInfo.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .uniqueId("uniqueId")
                                          .build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.PIPE_V0_TO_V1_CONVERSION))).thenReturn(true);
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(ngTriggerService.findTriggersByCriteria(any())).thenReturn(Collections.emptyList());
    when(conversionJobService.getJobByUuid(anyString())).thenReturn(Optional.empty());
    when(scopeResolutionHelper.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID))).thenReturn(scopeInfo);
  }

  // ---------------------------------------------------------------------------
  // P0: Feature flag gating
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHandle_FeatureFlagDisabled_SkipsProcessing() {
    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.PIPE_V0_TO_V1_CONVERSION))).thenReturn(false);
    ConversionJobEntity entity = buildSinglePipelineJob(ConversionStatus.QUEUED);

    conversionJobIterator.handle(entity);

    verify(conversionJobService, never()).updateJobStatus(anyString(), any(), any());
  }

  // ---------------------------------------------------------------------------
  // P0: Lock acquisition
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHandle_LockNotAcquired_SkipsProcessing() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(null);
    ConversionJobEntity entity = buildSinglePipelineJob(ConversionStatus.QUEUED);

    conversionJobIterator.handle(entity);

    verify(conversionJobService, never()).updateJobStatus(anyString(), any(), any());
  }

  // ---------------------------------------------------------------------------
  // P0: QUEUED → skip already V1 entity
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHandleQueued_AlreadyV1_MarksSkipped() {
    ConversionJobEntity entity = buildSinglePipelineJob(ConversionStatus.QUEUED);

    when(pmsPipelineService.getPipeline(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), eq(false),
             eq(false), eq(false), eq(false), any(), eq(true)))
        .thenReturn(Optional.of(
            PipelineEntity.builder().yaml(V0_YAML).name("test").harnessVersion(HarnessYamlVersion.V1).build()));

    conversionJobIterator.handle(entity);

    verify(conversionJobService).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.IN_PROGRESS), any());
    verify(conversionJobService).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.SKIPPED), any());
  }

  // ---------------------------------------------------------------------------
  // P0: savePipelineV1Yaml sets name from metadata
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testSavePipelineV1Yaml_SetsNameFromMetadata() {
    String pipelineName = "My Pipeline Name";
    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid(JOB_UUID)
                                     .status(ConversionStatus.IN_PROGRESS)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .actionType(ConversionActionType.SINGLE)
                                     .entityType(EntityType.PIPELINE)
                                     .entityIdentifier(PIPELINE_ID)
                                     .expanded(true)
                                     .yamlConverted(false)
                                     .entityMetadata(EntityMetadata.builder().yaml(V0_YAML).name(pipelineName).build())
                                     .build();

    when(conversionChecksumRepository.findByInlineEntity(
             anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Optional.empty());
    when(goConvertServiceClient.convert(any(), anyString(), any(), any(), any()))
        .thenReturn(ConvertResponse.newBuilder().setYaml(V1_YAML).setChecksum(CHECKSUM).build());
    when(conversionJobService.getChildJobs(eq(JOB_UUID))).thenReturn(Collections.emptyList());
    when(pmsInputSetService.list(any())).thenReturn(Collections.emptyList());

    conversionJobIterator.handle(entity);

    ArgumentCaptor<PipelineEntity> pipelineCaptor = ArgumentCaptor.forClass(PipelineEntity.class);
    verify(pmsPipelineService).validateAndCreatePipeline(pipelineCaptor.capture(), eq(false), eq(scopeInfo), eq(true));

    PipelineEntity savedPipeline = pipelineCaptor.getValue();
    assertThat(savedPipeline.getName()).isEqualTo(pipelineName);
    assertThat(savedPipeline.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
    assertThat(savedPipeline.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedPipeline.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(savedPipeline.getProjectIdentifier()).isEqualTo(PROJECT_ID);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testSavePipelineV1Yaml_FallsBackToIdentifierWhenNameNull() {
    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid(JOB_UUID)
                                     .status(ConversionStatus.IN_PROGRESS)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .actionType(ConversionActionType.SINGLE)
                                     .entityType(EntityType.PIPELINE)
                                     .entityIdentifier(PIPELINE_ID)
                                     .expanded(true)
                                     .yamlConverted(false)
                                     .entityMetadata(EntityMetadata.builder().yaml(V0_YAML).name(null).build())
                                     .build();

    when(conversionChecksumRepository.findByInlineEntity(
             anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Optional.empty());
    when(goConvertServiceClient.convert(any(), anyString(), any(), any(), any()))
        .thenReturn(ConvertResponse.newBuilder().setYaml(V1_YAML).setChecksum(CHECKSUM).build());
    when(conversionJobService.getChildJobs(eq(JOB_UUID))).thenReturn(Collections.emptyList());
    when(pmsInputSetService.list(any())).thenReturn(Collections.emptyList());

    conversionJobIterator.handle(entity);

    ArgumentCaptor<PipelineEntity> pipelineCaptor = ArgumentCaptor.forClass(PipelineEntity.class);
    verify(pmsPipelineService).validateAndCreatePipeline(pipelineCaptor.capture(), eq(false), eq(scopeInfo), eq(true));

    PipelineEntity savedPipeline = pipelineCaptor.getValue();
    assertThat(savedPipeline.getName()).isNotNull();
    assertThat(savedPipeline.getName()).contains(PIPELINE_ID);
  }

  // ---------------------------------------------------------------------------
  // P0: PROJECT expansion sets initial summary with totalEntities
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testProjectExpand_SetsInitialSummaryWithTotalEntities() {
    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid(JOB_UUID)
                                     .status(ConversionStatus.QUEUED)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .actionType(ConversionActionType.PROJECT)
                                     .entityType(EntityType.PIPELINE)
                                     .build();

    when(pmsPipelineService.listAllIdentifiers(any())).thenReturn(Arrays.asList("pipeline1", "pipeline2", "pipeline3"));

    conversionJobIterator.handle(entity);

    verify(conversionJobService).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.IN_PROGRESS), any());
    verify(conversionJobService, times(3)).createJob(any(ConversionJobEntity.class));

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).updateFirst(any(Query.class), updateCaptor.capture(), eq(ConversionJobEntity.class));
    String updateStr = updateCaptor.getValue().toString();
    assertThat(updateStr).contains("conversionMetrics");
    assertThat(updateStr).contains("expanded");
    assertThat(updateStr).contains("totalChildJobs");
  }

  // ---------------------------------------------------------------------------
  // P0: BATCH expansion sets initial summary
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBatchExpand_SetsInitialSummaryWithTotalEntities() {
    EntityIdentifierDTO ref1 =
        EntityIdentifierDTO.builder().entityId("pipeline1").entityType(EntityType.PIPELINE).build();
    EntityIdentifierDTO ref2 =
        EntityIdentifierDTO.builder().entityId("pipeline2").entityType(EntityType.PIPELINE).build();

    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid(JOB_UUID)
                                     .status(ConversionStatus.QUEUED)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .actionType(ConversionActionType.BATCH)
                                     .entityType(EntityType.PIPELINE)
                                     .entityReferences(Arrays.asList(ref1, ref2))
                                     .build();

    conversionJobIterator.handle(entity);

    verify(conversionJobService).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.IN_PROGRESS), any());
    verify(conversionJobService, times(2)).createJob(any(ConversionJobEntity.class));

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).updateFirst(any(Query.class), updateCaptor.capture(), eq(ConversionJobEntity.class));
    String updateStr = updateCaptor.getValue().toString();
    assertThat(updateStr).contains("conversionMetrics");
    assertThat(updateStr).contains("expanded");
  }

  // ---------------------------------------------------------------------------
  // P0: wakeParent updates parent summary progressively
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testWakeParent_UpdatesParentSummaryProgressively() {
    ConversionJobEntity child1 = buildChildJob("child-1", ConversionStatus.SUCCESS);
    ConversionJobEntity child2 = buildChildJob("child-2", ConversionStatus.FAILED);
    ConversionJobEntity child3 = buildChildJob("child-3", ConversionStatus.IN_PROGRESS);

    ConversionJobEntity parentEntity = ConversionJobEntity.builder()
                                           .uuid(PARENT_JOB_UUID)
                                           .status(ConversionStatus.IN_PROGRESS)
                                           .accountId(ACCOUNT_ID)
                                           .orgId(ORG_ID)
                                           .projectId(PROJECT_ID)
                                           .actionType(ConversionActionType.BATCH)
                                           .entityType(EntityType.PIPELINE)
                                           .expanded(true)
                                           .build();
    when(conversionJobService.getJobByUuid(eq(PARENT_JOB_UUID))).thenReturn(Optional.of(parentEntity));
    when(conversionJobService.getChildJobs(eq(PARENT_JOB_UUID))).thenReturn(Arrays.asList(child1, child2, child3));

    ConversionJobEntity completingChild =
        ConversionJobEntity.builder()
            .uuid("child-completing")
            .status(ConversionStatus.IN_PROGRESS)
            .accountId(ACCOUNT_ID)
            .orgId(ORG_ID)
            .projectId(PROJECT_ID)
            .actionType(ConversionActionType.SINGLE)
            .entityType(EntityType.PIPELINE)
            .entityIdentifier(PIPELINE_ID)
            .parentJobId(PARENT_JOB_UUID)
            .expanded(true)
            .yamlConverted(false)
            .entityMetadata(EntityMetadata.builder().yaml(V0_YAML).name("test").build())
            .build();

    when(conversionChecksumRepository.findByInlineEntity(
             anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Optional.empty());
    when(goConvertServiceClient.convert(any(), anyString(), any(), any(), any()))
        .thenReturn(ConvertResponse.newBuilder().setYaml(V1_YAML).setChecksum(CHECKSUM).build());
    when(conversionJobService.getChildJobs(eq("child-completing"))).thenReturn(Collections.emptyList());
    when(pmsInputSetService.list(any())).thenReturn(Collections.emptyList());

    conversionJobIterator.handle(completingChild);

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate, times(2))
        .updateFirst(any(Query.class), updateCaptor.capture(), eq(ConversionJobEntity.class));

    boolean parentSummaryUpdated = updateCaptor.getAllValues().stream().anyMatch(u -> {
      String s = u.toString();
      return s.contains("conversionMetrics") && s.contains("nextIteration");
    });
    assertThat(parentSummaryUpdated).isTrue();
  }

  // ---------------------------------------------------------------------------
  // P1: Aggregator check with all children done — computes final status
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testAggregatorCheck_AllChildrenSuccess_MarksSuccess() {
    ConversionJobEntity parentEntity = ConversionJobEntity.builder()
                                           .uuid(JOB_UUID)
                                           .status(ConversionStatus.IN_PROGRESS)
                                           .accountId(ACCOUNT_ID)
                                           .orgId(ORG_ID)
                                           .projectId(PROJECT_ID)
                                           .actionType(ConversionActionType.PROJECT)
                                           .entityType(EntityType.PIPELINE)
                                           .expanded(true)
                                           .build();

    ConversionJobEntity child1 = buildChildJob("child-1", ConversionStatus.SUCCESS);
    ConversionJobEntity child2 = buildChildJob("child-2", ConversionStatus.SUCCESS);

    when(conversionJobService.getChildJobs(eq(JOB_UUID))).thenReturn(Arrays.asList(child1, child2));

    conversionJobIterator.handle(parentEntity);

    verify(conversionJobService).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.SUCCESS), any());
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testAggregatorCheck_SomeChildrenFailed_MarksPartialSuccess() {
    ConversionJobEntity parentEntity = ConversionJobEntity.builder()
                                           .uuid(JOB_UUID)
                                           .status(ConversionStatus.IN_PROGRESS)
                                           .accountId(ACCOUNT_ID)
                                           .orgId(ORG_ID)
                                           .projectId(PROJECT_ID)
                                           .actionType(ConversionActionType.PROJECT)
                                           .entityType(EntityType.PIPELINE)
                                           .expanded(true)
                                           .build();

    ConversionJobEntity child1 = buildChildJob("child-1", ConversionStatus.SUCCESS);
    ConversionJobEntity child2 = buildChildJob("child-2", ConversionStatus.FAILED);

    when(conversionJobService.getChildJobs(eq(JOB_UUID))).thenReturn(Arrays.asList(child1, child2));

    conversionJobIterator.handle(parentEntity);

    verify(conversionJobService).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.PARTIAL_SUCCESS), any());
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testAggregatorCheck_AllChildrenFailed_MarksFailed() {
    ConversionJobEntity parentEntity = ConversionJobEntity.builder()
                                           .uuid(JOB_UUID)
                                           .status(ConversionStatus.IN_PROGRESS)
                                           .accountId(ACCOUNT_ID)
                                           .orgId(ORG_ID)
                                           .projectId(PROJECT_ID)
                                           .actionType(ConversionActionType.PROJECT)
                                           .entityType(EntityType.PIPELINE)
                                           .expanded(true)
                                           .build();

    ConversionJobEntity child1 = buildChildJob("child-1", ConversionStatus.FAILED);
    ConversionJobEntity child2 = buildChildJob("child-2", ConversionStatus.FAILED);

    when(conversionJobService.getChildJobs(eq(JOB_UUID))).thenReturn(Arrays.asList(child1, child2));

    conversionJobIterator.handle(parentEntity);

    verify(conversionJobService).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.FAILED), any());
  }

  // ---------------------------------------------------------------------------
  // P1: Aggregator check with children still running — sleeps
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testAggregatorCheck_ChildrenStillRunning_UpdatesSummaryAndSleeps() {
    ConversionJobEntity parentEntity = ConversionJobEntity.builder()
                                           .uuid(JOB_UUID)
                                           .status(ConversionStatus.IN_PROGRESS)
                                           .accountId(ACCOUNT_ID)
                                           .orgId(ORG_ID)
                                           .projectId(PROJECT_ID)
                                           .actionType(ConversionActionType.PROJECT)
                                           .entityType(EntityType.PIPELINE)
                                           .expanded(true)
                                           .build();

    ConversionJobEntity child1 = buildChildJob("child-1", ConversionStatus.SUCCESS);
    ConversionJobEntity child2 = buildChildJob("child-2", ConversionStatus.IN_PROGRESS);
    ConversionJobEntity child3 = buildChildJob("child-3", ConversionStatus.QUEUED);

    when(conversionJobService.getChildJobs(eq(JOB_UUID))).thenReturn(Arrays.asList(child1, child2, child3));

    conversionJobIterator.handle(parentEntity);

    verify(conversionJobService, never()).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.SUCCESS), any());
    verify(conversionJobService, never()).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.FAILED), any());

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate, times(2))
        .updateFirst(any(Query.class), updateCaptor.capture(), eq(ConversionJobEntity.class));

    boolean summaryUpdated =
        updateCaptor.getAllValues().stream().anyMatch(u -> u.toString().contains("conversionMetrics"));
    assertThat(summaryUpdated).isTrue();
  }

  // ---------------------------------------------------------------------------
  // P1: PROJECT expand with no pipelines — marks success immediately
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testProjectExpand_NoPipelines_MarksSuccess() {
    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid(JOB_UUID)
                                     .status(ConversionStatus.QUEUED)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .actionType(ConversionActionType.PROJECT)
                                     .entityType(EntityType.PIPELINE)
                                     .build();

    when(pmsPipelineService.listAllIdentifiers(any())).thenReturn(Collections.emptyList());

    conversionJobIterator.handle(entity);

    verify(conversionJobService, never()).createJob(any(ConversionJobEntity.class));
  }

  // ---------------------------------------------------------------------------
  // P1: Lock key generation — buildConversionEntityLockKey / resolveTemplateScope
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testLockKey_SinglePipeline_EntityScopedKey() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(null);
    ConversionJobEntity entity = buildSinglePipelineJob(ConversionStatus.QUEUED);

    conversionJobIterator.handle(entity);

    ArgumentCaptor<String> lockKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(persistentLocker)
        .waitToAcquireLockOptional(lockKeyCaptor.capture(), any(Duration.class), any(Duration.class));
    assertThat(lockKeyCaptor.getValue()).isEqualTo("ConversionEntity-account123/org1/project1/PIPELINE/myPipeline");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testLockKey_SingleTemplateWithAccountPrefix_ResolvesToAccountScope() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(null);
    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid(JOB_UUID)
                                     .status(ConversionStatus.QUEUED)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .actionType(ConversionActionType.SINGLE)
                                     .entityType(EntityType.TEMPLATE)
                                     .entityIdentifier("account.myTemplate")
                                     .build();

    conversionJobIterator.handle(entity);

    ArgumentCaptor<String> lockKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(persistentLocker)
        .waitToAcquireLockOptional(lockKeyCaptor.capture(), any(Duration.class), any(Duration.class));
    assertThat(lockKeyCaptor.getValue()).isEqualTo("ConversionEntity-account123///TEMPLATE/myTemplate");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testLockKey_SingleTemplateWithOrgPrefix_ResolvesToOrgScope() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(null);
    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid(JOB_UUID)
                                     .status(ConversionStatus.QUEUED)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .actionType(ConversionActionType.SINGLE)
                                     .entityType(EntityType.TEMPLATE)
                                     .entityIdentifier("org.sharedTemplate")
                                     .build();

    conversionJobIterator.handle(entity);

    ArgumentCaptor<String> lockKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(persistentLocker)
        .waitToAcquireLockOptional(lockKeyCaptor.capture(), any(Duration.class), any(Duration.class));
    assertThat(lockKeyCaptor.getValue()).isEqualTo("ConversionEntity-account123/org1//TEMPLATE/sharedTemplate");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testLockKey_BatchJob_UsesJobUuid() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(null);
    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid(JOB_UUID)
                                     .status(ConversionStatus.QUEUED)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .actionType(ConversionActionType.BATCH)
                                     .entityType(EntityType.PIPELINE)
                                     .build();

    conversionJobIterator.handle(entity);

    ArgumentCaptor<String> lockKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(persistentLocker)
        .waitToAcquireLockOptional(lockKeyCaptor.capture(), any(Duration.class), any(Duration.class));
    assertThat(lockKeyCaptor.getValue()).isEqualTo("ConversionJob-job-uuid-1");
  }

  // ---------------------------------------------------------------------------
  // P1: BATCH expand with empty references
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBatchExpand_EmptyReferences_MarksSuccess() {
    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid(JOB_UUID)
                                     .status(ConversionStatus.QUEUED)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .actionType(ConversionActionType.BATCH)
                                     .entityType(EntityType.PIPELINE)
                                     .entityReferences(Collections.emptyList())
                                     .build();

    conversionJobIterator.handle(entity);

    verify(conversionJobService).updateJobStatus(eq(JOB_UUID), eq(ConversionStatus.SUCCESS), any());
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ConversionJobEntity buildSinglePipelineJob(ConversionStatus status) {
    return ConversionJobEntity.builder()
        .uuid(JOB_UUID)
        .status(status)
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .actionType(ConversionActionType.SINGLE)
        .entityType(EntityType.PIPELINE)
        .entityIdentifier(PIPELINE_ID)
        .build();
  }

  private ConversionJobEntity buildChildJob(String uuid, ConversionStatus status) {
    return ConversionJobEntity.builder()
        .uuid(uuid)
        .status(status)
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .actionType(ConversionActionType.SINGLE)
        .entityType(EntityType.PIPELINE)
        .entityIdentifier("pipeline_" + uuid)
        .parentJobId(JOB_UUID)
        .build();
  }
}
