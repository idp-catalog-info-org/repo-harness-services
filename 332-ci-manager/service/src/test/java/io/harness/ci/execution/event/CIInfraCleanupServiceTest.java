/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.event;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ABHIJEET_GUPTA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SATYA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.CIResourceCleanup.CIResourceCleanupResponseKeys;
import io.harness.app.beans.entities.InfraResourceDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.event.CIInfraCleanupService;
import io.harness.ci.execution.execution.StageCleanupUtility;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.metrics.CIObservabilityConstants;
import io.harness.ci.metrics.ExecutionMetricsService;
import io.harness.ci.metrics.helper.CIMetricsHelper;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class CIInfraCleanupServiceTest extends CIExecutionTestBase {
  @Mock HPersistence persistence;
  @Mock KryoSerializer kryoSerializer;
  @Mock StageCleanupUtility stageCleanupUtility;
  @Mock ExecutionMetricsService executionMetricsService;
  @Mock ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock Query<CIResourceCleanup> mockQuery;
  @Mock UpdateOperations<CIResourceCleanup> mockUpdateOperations;
  @InjectMocks CIInfraCleanupService ciInfraCleanupService;
  InfraResourceDetails infraResourceDetails;
  CIResourceCleanup ciResourceCleanup;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    infraResourceDetails = InfraResourceDetails.builder().build();
    ciResourceCleanup = CIResourceCleanup.builder().build();

    FieldEnd fieldEnd = mock(FieldEnd.class);
    when(persistence.createUpdateOperations(CIResourceCleanup.class)).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.set(anyString(), any())).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.inc(anyString(), any())).thenReturn(mockUpdateOperations);
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(mockQuery.field(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.lessThan(any())).thenReturn(mockQuery);
    when(fieldEnd.equal(any())).thenReturn(mockQuery);
    when(fieldEnd.notEqual(any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNoDocumentFound() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(null);
    ciInfraCleanupService.run();
    verify(persistence, times(0)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(0)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(0)).submitCleanupRequest(any(), any(), any(Boolean.class));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testResourceDetailsNotFound() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(null);
    ciInfraCleanupService.run();
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(1)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(0)).submitCleanupRequest(any(), any(), any(Boolean.class));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testExpiredDocuments() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(infraResourceDetails);
    ciResourceCleanup.setRetryCount(11);
    ciInfraCleanupService.run();
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(1)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(0)).submitCleanupRequest(any(), any(), any(Boolean.class));
    ciResourceCleanup.setRetryCount(0);
    ciResourceCleanup.setCreatedAt(0);
    ciInfraCleanupService.run();
    verify(persistence, times(2)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(2)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(0)).submitCleanupRequest(any(), any(), any(Boolean.class));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testEligibleDocuments() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(infraResourceDetails);
    ciResourceCleanup.setCreatedAt(System.currentTimeMillis());
    ciInfraCleanupService.run();
    verify(persistence, times(0)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(1)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(1)).submitCleanupRequest(any(), any(), eq(false));
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testReaperQueryDoesNotFilterOnDeferredByFF() throws InterruptedException {
    // Rows skipped by CI_SKIP_CLOUD_VM_CLEANUP are kept out by their pushed-out
    // processAfter, not by an explicit deferredByFF filter. Confirm the reaper
    // query no longer touches the deferredByFF field — it's purely an audit marker.
    when(persistence.findAndModify(any(), any(), any())).thenReturn(null);
    ciInfraCleanupService.run();
    verify(mockQuery, never()).field(eq(CIResourceCleanupResponseKeys.deferredByFF));
    verify(mockQuery, times(1)).field(eq(CIResourceCleanupResponseKeys.processAfter));
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testDeferredRowAgedOver24hIsNotDeleted() throws InterruptedException {
    // The 24h application-level TTL guard exists for rows whose cleanup ack
    // never came back. Deferred-by-FF rows have a deliberate 2-day delay, so
    // the guard would mis-fire and orphan the VM. Bypass for those rows.
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(infraResourceDetails);
    ciResourceCleanup.setRetryCount(1);
    ciResourceCleanup.setCreatedAt(0); // ancient — would normally trigger TTL delete
    ciResourceCleanup.setDeferredByFF(true);

    ciInfraCleanupService.run();

    verify(persistence, never()).delete((Query<PersistentEntity>) any());
    verify(stageCleanupUtility, times(1)).submitCleanupRequest(any(), any(), eq(true));
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testNonDeferredRowAgedOver24hIsStillDeleted() throws InterruptedException {
    // Regression for the existing safety net: rows that were not deliberately
    // deferred must still be GC'd at 24h.
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(infraResourceDetails);
    ciResourceCleanup.setRetryCount(0);
    ciResourceCleanup.setCreatedAt(0);
    ciResourceCleanup.setDeferredByFF(null);

    ciInfraCleanupService.run();

    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
    verify(stageCleanupUtility, never()).submitCleanupRequest(any(), any(), any(Boolean.class));
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testDeferredRowOverRetryCapIsStillDeleted() throws InterruptedException {
    // The retry-count cap (10) bounds runaway loops even for deferred rows.
    // Without this we'd keep re-dispatching forever if the cleanup task
    // genuinely could not be processed (e.g., runner farm extended outage).
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(infraResourceDetails);
    ciResourceCleanup.setRetryCount(11);
    ciResourceCleanup.setCreatedAt(System.currentTimeMillis());
    ciResourceCleanup.setDeferredByFF(true);

    ciInfraCleanupService.run();

    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
    verify(stageCleanupUtility, never()).submitCleanupRequest(any(), any(), any(Boolean.class));
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testEligibleRowPassesDeferredFlagThrough() throws InterruptedException {
    // Reaper must thread the row's deferredByFF flag down to submitCleanupRequest
    // so that shouldSkipCloudVmCleanup can force-cleanup on the second pickup.
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(infraResourceDetails);
    ciResourceCleanup.setRetryCount(1);
    ciResourceCleanup.setCreatedAt(System.currentTimeMillis());
    ciResourceCleanup.setDeferredByFF(true);

    ciInfraCleanupService.run();

    verify(stageCleanupUtility, times(1)).submitCleanupRequest(any(), any(), eq(true));
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testDispatchedCleanupRecordsRetryMetric() throws InterruptedException {
    // The reaper is the only signal for FF-deferred cleanups, so a dispatch here must be counted separately from the
    // inline cleanup_infra operation.
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any()))
        .thenReturn(InfraResourceDetails.builder().ambiance(ciAmbiance()).build());
    ciResourceCleanup.setRetryCount(1);
    ciResourceCleanup.setCreatedAt(System.currentTimeMillis());
    when(stageCleanupUtility.submitCleanupRequest(any(), any(), any(Boolean.class)))
        .thenReturn(new StageCleanupUtility.CleanupSubmitResult(true, "HostedVm"));

    ciInfraCleanupService.run();

    verify(executionMetricsService)
        .recordSystemApiCall(eq("acc"), eq("HostedVm"), eq(CIObservabilityConstants.OP_CLEANUP_INFRA_RETRY),
            eq(CIObservabilityConstants.OUTCOME_SUCCESS), eq(CIObservabilityConstants.PHASE_SUBMIT), isNull());
    verify(executionSweepingOutputResolver, never()).resolveOptional(any(), any());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testFailedCleanupRecordsRetryMetricFromStageInfraDetails() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any()))
        .thenReturn(InfraResourceDetails.builder().ambiance(ciAmbiance()).build());
    ciResourceCleanup.setRetryCount(1);
    ciResourceCleanup.setCreatedAt(System.currentTimeMillis());
    when(stageCleanupUtility.submitCleanupRequest(any(), any(), any(Boolean.class)))
        .thenThrow(new RuntimeException("dispatch failed"));

    ciInfraCleanupService.run();

    verify(executionMetricsService)
        .recordSystemApiCall(eq("acc"), eq(CIObservabilityConstants.INFRA_TYPE_UNKNOWN),
            eq(CIObservabilityConstants.OP_CLEANUP_INFRA_RETRY), eq(CIObservabilityConstants.OUTCOME_SYSTEM_FAILURE),
            eq(CIObservabilityConstants.PHASE_SUBMIT), isNull());
    verify(executionSweepingOutputResolver).resolveOptional(any(), any());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testUnprovisionedCleanupDoesNotRecordRetryFailure() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any()))
        .thenReturn(InfraResourceDetails.builder().ambiance(ciAmbiance()).build());
    ciResourceCleanup.setRetryCount(1);
    ciResourceCleanup.setCreatedAt(System.currentTimeMillis());
    when(stageCleanupUtility.submitCleanupRequest(any(), any(), any(Boolean.class)))
        .thenThrow(new CIStageExecutionException(CIMetricsHelper.UNPROVISIONED_CLEANUP_MESSAGE));

    ciInfraCleanupService.run();

    verify(executionMetricsService, never())
        .recordSystemApiCall(anyString(), anyString(), eq(CIObservabilityConstants.OP_CLEANUP_INFRA_RETRY), anyString(),
            anyString(), any());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testStoStageDoesNotRecordRetryMetric() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any()))
        .thenReturn(InfraResourceDetails.builder().ambiance(stoAmbiance()).build());
    ciResourceCleanup.setRetryCount(1);
    ciResourceCleanup.setCreatedAt(System.currentTimeMillis());
    when(stageCleanupUtility.submitCleanupRequest(any(), any(), any(Boolean.class)))
        .thenReturn(new StageCleanupUtility.CleanupSubmitResult(true, "KubernetesDirect"));

    ciInfraCleanupService.run();

    verify(executionMetricsService, never())
        .recordSystemApiCall(anyString(), anyString(), eq(CIObservabilityConstants.OP_CLEANUP_INFRA_RETRY), anyString(),
            anyString(), any());
  }

  private Ambiance ciAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "acc")
        .addLevels(Level.newBuilder()
                       .setStepType(StepType.newBuilder()
                                        .setStepCategory(StepCategory.STAGE)
                                        .setType("IntegrationStageStepPMS")
                                        .build())
                       .build())
        .build();
  }

  private Ambiance stoAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "acc")
        .addLevels(
            Level.newBuilder()
                .setStepType(
                    StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("SecurityStageStepPMS").build())
                .build())
        .build();
  }
}
