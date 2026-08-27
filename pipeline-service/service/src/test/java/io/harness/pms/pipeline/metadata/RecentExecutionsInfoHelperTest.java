/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.metadata;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.pms.pipeline.metadata.RecentExecutionsInfoHelper.NUM_RECENT_EXECUTIONS;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.lock.noop.AcquiredNoopLock;
import io.harness.observer.Subject.Informant0;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.RecentExecutionInfo;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class RecentExecutionsInfoHelperTest extends CategoryTest {
  RecentExecutionsInfoHelper recentExecutionsInfoHelper;
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock PersistentLocker persistentLocker;

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";
  String pipelineId = "pipeline";
  String parentUniqueId = "someRandomId";

  String expectedLockName = "recentExecutionsInfo/acc/org/proj/pipeline";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    recentExecutionsInfoHelper = new RecentExecutionsInfoHelper(pipelineMetadataService, persistentLocker);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testOnExecutionStart() {
    doReturn(AcquiredNoopLock.builder().build())
        .when(persistentLocker)
        .waitToAcquireLockOptional(expectedLockName, Duration.ofSeconds(1), Duration.ofSeconds(2));
    PipelineMetadataV2 pipelineMetadata =
        PipelineMetadataV2.builder().recentExecutionInfoList(new LinkedList<>()).build();
    doReturn(Optional.of(pipelineMetadata))
        .when(pipelineMetadataService)
        .getMetadata(accountIdentifier, "unique-id", pipelineId);
    PlanExecution planExecution = PlanExecution.builder().metadata(ExecutionMetadata.newBuilder().build()).build();
    recentExecutionsInfoHelper.onExecutionStart(
        getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier), pipelineId, planExecution, false);
    verify(pipelineMetadataService, times(1)).update(any(), any());
    assertThat(pipelineMetadata.getRecentExecutionInfoList()).hasSize(1);

    PipelineMetadataV2 pipelineMetadataWithNullList = PipelineMetadataV2.builder().build();
    doReturn(Optional.of(pipelineMetadataWithNullList))
        .when(pipelineMetadataService)
        .getMetadata(accountIdentifier, "unique-id", pipelineId);
    recentExecutionsInfoHelper.onExecutionStart(
        getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier), pipelineId, planExecution, false);
    verify(pipelineMetadataService, times(2)).update(any(), any());
  }

  private ScopeInfo getScopeInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return ScopeInfo.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .uniqueId("unique-id")
        .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testOnExecutionStartForRollbackModeExecution() {
    recentExecutionsInfoHelper.onExecutionStart(null, null,
        PlanExecution.builder()
            .metadata(ExecutionMetadata.newBuilder().setExecutionMode(PIPELINE_ROLLBACK).build())
            .build(),
        false);
    verify(persistentLocker, times(0)).waitToAcquireLockOptional(any(), any(), any());
    verify(pipelineMetadataService, times(0)).getMetadata(any(), any(), any(), any());
    verify(pipelineMetadataService, times(0)).update(any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testOnExecutionUpdateForRollbackModeExecution() {
    recentExecutionsInfoHelper.onExecutionUpdate(
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(PIPELINE_ROLLBACK).build())
            .build(),
        null, null, false, null);
    verify(persistentLocker, times(0)).waitToAcquireLockOptional(any(), any(), any());
    verify(pipelineMetadataService, times(0)).getMetadata(any(), any(), any(), any());
    verify(pipelineMetadataService, times(0)).update(any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateMetadata() {
    List<String> testList = new ArrayList<>();
    Informant0<List<RecentExecutionInfo>> subject =
        (List<RecentExecutionInfo> recentExecutionInfoList) -> testList.add("testString");
    doReturn(AcquiredNoopLock.builder().build())
        .when(persistentLocker)
        .waitToAcquireLockOptional(expectedLockName, Duration.ofSeconds(1), Duration.ofSeconds(2));
    PipelineMetadataV2 pipelineMetadata =
        PipelineMetadataV2.builder().recentExecutionInfoList(new LinkedList<>()).build();
    doReturn(Optional.of(pipelineMetadata))
        .when(pipelineMetadataService)
        .getMetadata(accountIdentifier, "unique-id", pipelineId);
    recentExecutionsInfoHelper.updateMetadata(getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier),
        pipelineId, subject, PlanExecution.builder().build(), false);

    // the list will contain this element only if the subject was called
    assertThat(testList).containsExactly("testString");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateMetadataWithNoLockAcquired() {
    Informant0<List<RecentExecutionInfo>> subject = (List<RecentExecutionInfo> recentExecutionInfoList) -> {
      throw new InvalidRequestException("This subject was not supposed to be called");
    };
    doReturn(null)
        .when(persistentLocker)
        .waitToAcquireLockOptional(expectedLockName, Duration.ofSeconds(1), Duration.ofSeconds(2));
    recentExecutionsInfoHelper.updateMetadata(getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier),
        pipelineId, subject, PlanExecution.builder().build(), false);
    verify(pipelineMetadataService, times(0)).getMetadata(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateMetadataWithNoPipelineMetadataFound() {
    Informant0<List<RecentExecutionInfo>> subject = (List<RecentExecutionInfo> recentExecutionInfoList) -> {
      throw new InvalidRequestException("This subject was not supposed to be called");
    };
    doReturn(AcquiredNoopLock.builder().build())
        .when(persistentLocker)
        .waitToAcquireLockOptional(expectedLockName, Duration.ofSeconds(1), Duration.ofSeconds(2));
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(accountIdentifier, "unique-id", pipelineId);
    // if this calls the subject then the exception inside will be thrown that'll fail the test
    recentExecutionsInfoHelper.updateMetadata(getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier),
        pipelineId, subject, PlanExecution.builder().build(), false);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateMetadataWithNotRecentPlanExecutionUpdate() {
    Informant0<List<RecentExecutionInfo>> subject = (List<RecentExecutionInfo> recentExecutionInfoList) -> {
      throw new InvalidRequestException("This subject was not supposed to be called");
    };
    doReturn(AcquiredNoopLock.builder().build())
        .when(persistentLocker)
        .waitToAcquireLockOptional(expectedLockName, Duration.ofSeconds(1), Duration.ofSeconds(2));
    List<RecentExecutionInfo> recentExecutionInfoList = new ArrayList<>();
    // Adding recentExecutions.
    for (int i = 0; i < NUM_RECENT_EXECUTIONS; i++) {
      recentExecutionInfoList.add(RecentExecutionInfo.builder().startTs(100L + i).build());
    }
    doReturn(Optional.of(PipelineMetadataV2.builder().recentExecutionInfoList(recentExecutionInfoList).build()))
        .when(pipelineMetadataService)
        .getMetadata(accountIdentifier, "unique-id", pipelineId);
    // if this calls the subject then the exception inside will be thrown that'll fail the test.
    // Since PlanExecution.startTs is < earliest recentExecution.startTs. Subject should not be called.
    recentExecutionsInfoHelper.updateMetadata(getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier),
        pipelineId, subject, PlanExecution.builder().startTs(50L).build(), false);

    List<String> testList = new ArrayList<>();
    subject = (List<RecentExecutionInfo> recentExecutionInfoListV1) -> testList.add("testString");

    // Since PlanExecution.startTs is > earliest recentExecution.startTs. Subject should be invoked.
    recentExecutionsInfoHelper.updateMetadata(getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier),
        pipelineId, subject, PlanExecution.builder().startTs(1000L).build(), false);
    // Since subject will be invoked. testList should have 1 item.
    assertThat(testList.contains("testString")).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetLockName() {
    assertThat(recentExecutionsInfoHelper.getLockName(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId))
        .isEqualTo(expectedLockName);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetCriteriaForPipelineMetadata() {
    Criteria criteria = recentExecutionsInfoHelper.getCriteriaForPipelineMetadata(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.size()).isEqualTo(4);
    assertThat(criteriaObject.get("accountIdentifier")).isEqualTo(accountIdentifier);
    assertThat(criteriaObject.get("orgIdentifier")).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get("projectIdentifier")).isEqualTo(projectIdentifier);
    assertThat(criteriaObject.get("identifier")).isEqualTo(pipelineId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetUpdateOperationForRecentExecutionInfo() {
    List<RecentExecutionInfo> recentExecutionInfoList =
        Arrays.asList(RecentExecutionInfo.builder().planExecutionId("p1").build(),
            RecentExecutionInfo.builder().planExecutionId("p2").build());
    Update update = recentExecutionsInfoHelper.getUpdateOperationForRecentExecutionInfo(recentExecutionInfoList);
    Document updateObject = update.getUpdateObject();
    assertThat(updateObject.size()).isEqualTo(1);
    Document setObject = (Document) updateObject.get("$set");
    assertThat(setObject.size()).isEqualTo(1);
    assertThat(setObject.get("recentExecutionInfoList")).isEqualTo(recentExecutionInfoList);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetCriteriaForPipelineMetadataWithParentUniqueId() {
    Criteria criteria =
        recentExecutionsInfoHelper.getCriteriaForPipelineMetadata(accountIdentifier, parentUniqueId, pipelineId);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.size()).isEqualTo(3);
    assertThat(criteriaObject.get("accountIdentifier")).isEqualTo(accountIdentifier);
    assertThat(criteriaObject.get("parentUniqueId")).isEqualTo(parentUniqueId);
    assertThat(criteriaObject.get("identifier")).isEqualTo(pipelineId);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testUpdateMetadataWithParentIdQuerying() {
    List<String> testList = new ArrayList<>();
    Informant0<List<RecentExecutionInfo>> subject =
        (List<RecentExecutionInfo> recentExecutionInfoList) -> testList.add("testString");
    doReturn(AcquiredNoopLock.builder().build())
        .when(persistentLocker)
        .waitToAcquireLockOptional(expectedLockName, Duration.ofSeconds(1), Duration.ofSeconds(2));
    PipelineMetadataV2 pipelineMetadata =
        PipelineMetadataV2.builder().recentExecutionInfoList(new LinkedList<>()).build();
    doReturn(Optional.of(pipelineMetadata))
        .when(pipelineMetadataService)
        .getMetadata(accountIdentifier, parentUniqueId, pipelineId);
    recentExecutionsInfoHelper.updateMetadata(ScopeInfo.builder()
                                                  .accountIdentifier(accountIdentifier)
                                                  .orgIdentifier(orgIdentifier)
                                                  .projectIdentifier(projectIdentifier)
                                                  .uniqueId(parentUniqueId)
                                                  .build(),
        pipelineId, subject, PlanExecution.builder().build(), true);

    assertThat(testList).containsExactly("testString");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testOnExecutionUpdateStoresFailureInfo() {
    doReturn(AcquiredNoopLock.builder().build())
        .when(persistentLocker)
        .waitToAcquireLockOptional(expectedLockName, Duration.ofSeconds(1), Duration.ofSeconds(2));
    RecentExecutionInfo recentExecutionInfo =
        RecentExecutionInfo.builder().planExecutionId("plan1").status(Status.RUNNING).startTs(100L).build();
    PipelineMetadataV2 pipelineMetadata =
        PipelineMetadataV2.builder().recentExecutionInfoList(new LinkedList<>(List.of(recentExecutionInfo))).build();
    doReturn(Optional.of(pipelineMetadata))
        .when(pipelineMetadataService)
        .getMetadata(accountIdentifier, "unique-id", pipelineId);
    FailureInfo expectedFailureInfo = FailureInfo.newBuilder().setErrorMessage("pipeline failed").build();
    recentExecutionsInfoHelper.onExecutionUpdate(
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build())
            .build(),
        PlanExecution.builder().uuid("plan1").status(Status.FAILED).endTs(200L).startTs(100L).build(),
        getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier), false, expectedFailureInfo);
    assertThat(recentExecutionInfo.getStatus()).isEqualTo(Status.FAILED);
    assertThat(recentExecutionInfo.getEndTs()).isEqualTo(200L);
    assertThat(recentExecutionInfo.getFailureInfo()).isEqualTo(expectedFailureInfo);
    verify(pipelineMetadataService, times(1)).update(any(), any());
  }
}
