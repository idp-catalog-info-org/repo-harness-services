/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.ci.commonconstants.CIExecutionConstants.OPTIMIZATION_STATE_FULL_RUN;
import static io.harness.ci.commonconstants.CIExecutionConstants.OPTIMIZATION_STATE_OPTIMIZED;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.steps.CIPipelineBaseline;
import io.harness.beans.steps.CIStageSavingsInfo;
import io.harness.beans.steps.CIStageTelemetryData;
import io.harness.beans.steps.CITelemetryInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.utils.HarnessTokenUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.CIStageOptimizationState;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CIPipelineBaselineRespository;
import io.harness.repositories.CIStageSavingsInfoRepository;
import io.harness.repositories.CIStageTelemetryRepository;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;
import io.harness.utils.CIScopeInfoHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CIPipelineEndEventHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccountId";
  private static final String ORG_ID = "testOrgId";
  private static final String PROJECT_ID = "testProjectId";
  private static final String PIPELINE_ID = "testPipelineId";
  private static final String PLAN_EXECUTION_ID = "testPlanExecutionId";
  private static final String PARENT_UNIQUE_ID = "testParentUniqueId";
  private static final String EMAIL = "test@harness.io";

  @Mock private CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private CIPipelineBaselineRespository ciPipelineBaselineRespository;
  @Mock private CIStageTelemetryRepository ciStageTelemetryRepository;
  @Mock private CIStageSavingsInfoRepository ciStageSavingsInfoRepository;
  @Mock private CIScopeInfoHelper scopeInfoHelper;
  @Mock private CIFeatureFlagService ciFeatureFlagService;
  @Mock private HarnessTokenUtils harnessTokenUtils;

  @InjectMocks private CIPipelineEndEventHandler handler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private Ambiance buildAmbiance(long startTs) {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", ACCOUNT_ID)
        .putSetupAbstractions("orgIdentifier", ORG_ID)
        .putSetupAbstractions("projectIdentifier", PROJECT_ID)
        .putSetupAbstractions("parentUniqueId", PARENT_UNIQUE_ID)
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setStartTs(startTs)
        .setMetadata(
            ExecutionMetadata.newBuilder()
                .setPipelineIdentifier(PIPELINE_ID)
                .setRunSequence(5)
                .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                    .setTriggeredBy(TriggeredBy.newBuilder().putExtraInfo("email", EMAIL).build())
                                    .build())
                .build())
        .build();
  }

  private CIPipelineModuleInfo buildModuleInfo(Boolean isPrivateRepo, List<CIImageDetails> imageDetails,
      List<CIScmDetails> scmDetails, List<CIInfraDetails> infraDetails, List<TIBuildDetails> tiBuildDetails,
      List<CIStageOptimizationState> optimizationStates) {
    return CIPipelineModuleInfo.builder()
        .isPrivateRepo(isPrivateRepo)
        .branch("main")
        .buildType("branch")
        .repoName("test-repo")
        .imageDetailsList(imageDetails != null ? imageDetails : Collections.emptyList())
        .scmDetailsList(scmDetails)
        .infraDetailsList(infraDetails != null ? infraDetails : Collections.emptyList())
        .tiBuildDetailsList(tiBuildDetails)
        .ciStageOptimizationStateList(optimizationStates)
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_whenModuleInfoIsNotCIType_shouldDoNothing() {
    PipelineModuleInfo nonCiModuleInfo = new PipelineModuleInfo() {};
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(buildAmbiance(1000L)).moduleInfo(nonCiModuleInfo).endTs(2000L).build();

    assertThatCode(() -> handler.handleEvent(event))
        .as("Should not throw when moduleInfo is not CIPipelineModuleInfo")
        .doesNotThrowAnyException();

    verify(ciAccountExecutionMetadataRepository, never()).updateAccountExecutionMetadata(any(), anyLong());
    verify(telemetryReporter, never()).sendTrackEvent(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_whenModuleInfoIsNull_shouldDoNothing() {
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(buildAmbiance(1000L)).moduleInfo(null).endTs(2000L).build();

    assertThatCode(() -> handler.handleEvent(event))
        .as("Should not throw when moduleInfo is null")
        .doesNotThrowAnyException();

    verify(ciAccountExecutionMetadataRepository, never()).updateAccountExecutionMetadata(any(), anyLong());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testHandleEvent_cleansUpScopedToken() {
    Ambiance ambiance = buildAmbiance(1000L);
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, null);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(harnessTokenUtils).cleanupHarnessToken(ambiance, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testHandleEvent_cleansUpScopedToken_evenWhenModuleInfoNotCIType() {
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(new PipelineModuleInfo() {}).endTs(2000L).build();

    handler.handleEvent(event);

    verify(harnessTokenUtils).cleanupHarnessToken(ambiance, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_whenPrivateRepo_shouldUpdateExecutionCount() {
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(true, Collections.emptyList(), null, Collections.emptyList(), null, null);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(ciAccountExecutionMetadataRepository).updateAccountExecutionMetadata(eq(ACCOUNT_ID), eq(2000L));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_whenNotPrivateRepo_shouldNotUpdateExecutionCount() {
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, null);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(ciAccountExecutionMetadataRepository, never()).updateAccountExecutionMetadata(any(), anyLong());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withScmDetails_shouldSendTelemetry() {
    List<CIScmDetails> scmDetails = Arrays.asList(CIScmDetails.builder()
                                                      .scmUrl("https://github.com/test/repo")
                                                      .scmProvider("GitHub")
                                                      .scmAuthType("OAuth")
                                                      .scmHostType("SaaS")
                                                      .build());
    List<CIImageDetails> imageDetails =
        Arrays.asList(CIImageDetails.builder().imageName("alpine").imageTag("3.18").build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, imageDetails, scmDetails, Collections.emptyList(), null, null);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(telemetryReporter).sendTrackEvent(eq("ci_built"), eq(EMAIL), eq(ACCOUNT_ID), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withHarnessHostedInfra_shouldIncludeResourceClassAndImageDetails() {
    List<CIInfraDetails> infraDetails = Arrays.asList(CIInfraDetails.builder()
                                                          .infraType("VM")
                                                          .infraOSType("Linux")
                                                          .infraHostType("Harness Hosted")
                                                          .infraArchType("amd64")
                                                          .resourceClass("standard")
                                                          .imageName("default-image")
                                                          .customImage(true)
                                                          .connectorIdentifier("connector1")
                                                          .nestedVirtualization(true)
                                                          .build());
    CIPipelineModuleInfo moduleInfo = buildModuleInfo(false, Collections.emptyList(), null, infraDetails, null, null);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(telemetryReporter).sendTrackEvent(eq("ci_built"), eq(EMAIL), eq(ACCOUNT_ID), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withNonHarnessHostedInfra_shouldNotIncludeResourceClass() {
    List<CIInfraDetails> infraDetails = Arrays.asList(CIInfraDetails.builder()
                                                          .infraType("K8s")
                                                          .infraOSType("Linux")
                                                          .infraHostType("Self Hosted")
                                                          .infraArchType("amd64")
                                                          .resourceClass("")
                                                          .imageName("")
                                                          .customImage(false)
                                                          .connectorIdentifier("")
                                                          .nestedVirtualization(false)
                                                          .build());
    CIPipelineModuleInfo moduleInfo = buildModuleInfo(false, Collections.emptyList(), null, infraDetails, null, null);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(telemetryReporter).sendTrackEvent(eq("ci_built"), eq(EMAIL), eq(ACCOUNT_ID), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withTIBuildDetails_shouldIncludeTIData() {
    List<TIBuildDetails> tiBuildDetails =
        Arrays.asList(TIBuildDetails.builder().buildTool("Maven").language("Java").build(),
            TIBuildDetails.builder().buildTool("Gradle").language("Kotlin").build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), tiBuildDetails, null);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(telemetryReporter).sendTrackEvent(eq("ci_built"), eq(EMAIL), eq(ACCOUNT_ID), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withOptimizationStateFullRun_shouldUpdateBaseline() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_FULL_RUN).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(ciPipelineBaselineRespository)
        .upsert(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), eq(PARENT_UNIQUE_ID),
            eq(PLAN_EXECUTION_ID), eq(1000L));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withOptimizationStateOptimized_andNoExistingBaseline_shouldUpdateBaseline() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_OPTIMIZED).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    when(ciFeatureFlagService.isEnabled(any(), eq(ACCOUNT_ID))).thenReturn(true);
    when(ciPipelineBaselineRespository.findByParentUniqueIdAndPipelineId(eq(PARENT_UNIQUE_ID), eq(PIPELINE_ID)))
        .thenReturn(null);

    handler.handleEvent(event);

    verify(ciPipelineBaselineRespository)
        .upsert(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), eq(PARENT_UNIQUE_ID), eq(null), eq(1000L));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withOptimizationStateOptimized_andTimeTakenGreaterThanBaseline_shouldUpdate() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_OPTIMIZED).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(5000L).build();

    CIPipelineBaseline existingBaseline = CIPipelineBaseline.builder().baselineMs(2000L).build();
    when(ciFeatureFlagService.isEnabled(any(), eq(ACCOUNT_ID))).thenReturn(true);
    when(ciPipelineBaselineRespository.findByParentUniqueIdAndPipelineId(eq(PARENT_UNIQUE_ID), eq(PIPELINE_ID)))
        .thenReturn(existingBaseline);

    handler.handleEvent(event);

    verify(ciPipelineBaselineRespository)
        .upsert(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), eq(PARENT_UNIQUE_ID), eq(null), eq(4000L));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withOptimizationStateOptimized_andTimeTakenLessThanBaseline_shouldNotUpdate() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_OPTIMIZED).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    CIPipelineBaseline existingBaseline = CIPipelineBaseline.builder().baselineMs(5000L).build();
    when(ciFeatureFlagService.isEnabled(any(), eq(ACCOUNT_ID))).thenReturn(true);
    when(ciPipelineBaselineRespository.findByParentUniqueIdAndPipelineId(eq(PARENT_UNIQUE_ID), eq(PIPELINE_ID)))
        .thenReturn(existingBaseline);

    handler.handleEvent(event);

    verify(ciPipelineBaselineRespository, never()).upsert(any(), any(), any(), any(), any(), any(), anyLong());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withOptimizationStateDisabled_shouldNotUpdateBaseline() {
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, null);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(ciPipelineBaselineRespository, never()).upsert(any(), any(), any(), any(), any(), any(), anyLong());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_whenStartTsIsZero_shouldNotUpdateBaseline() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_FULL_RUN).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance = buildAmbiance(0L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    handler.handleEvent(event);

    verify(ciPipelineBaselineRespository, never()).upsert(any(), any(), any(), any(), any(), any(), anyLong());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_whenEndTsIsZero_shouldNotUpdateBaseline() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_FULL_RUN).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(0L).build();

    handler.handleEvent(event);

    verify(ciPipelineBaselineRespository, never()).upsert(any(), any(), any(), any(), any(), any(), anyLong());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_whenBaselineUpdateThrows_shouldCatchAndContinue() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_FULL_RUN).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(true, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    doThrow(new RuntimeException("DB error"))
        .when(ciPipelineBaselineRespository)
        .upsert(any(), any(), any(), any(), any(), any(), anyLong());

    assertThatCode(() -> handler.handleEvent(event))
        .as("Should catch exception from baseline update without rethrowing")
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withOptimizedAndFullRunStages_shouldReturnOptimized() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_FULL_RUN).build(),
            CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_OPTIMIZED).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    when(ciFeatureFlagService.isEnabled(any(), eq(ACCOUNT_ID))).thenReturn(true);
    when(ciPipelineBaselineRespository.findByParentUniqueIdAndPipelineId(eq(PARENT_UNIQUE_ID), eq(PIPELINE_ID)))
        .thenReturn(null);

    handler.handleEvent(event);

    verify(ciPipelineBaselineRespository)
        .upsert(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), eq(PARENT_UNIQUE_ID), eq(null), eq(1000L));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withNoParentUniqueId_shouldUseScopeInfoHelper() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_FULL_RUN).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", ACCOUNT_ID)
            .putSetupAbstractions("orgIdentifier", ORG_ID)
            .putSetupAbstractions("projectIdentifier", PROJECT_ID)
            .setPlanExecutionId(PLAN_EXECUTION_ID)
            .setStartTs(1000L)
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setPipelineIdentifier(PIPELINE_ID)
                    .setRunSequence(5)
                    .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                        .setTriggeredBy(TriggeredBy.newBuilder().putExtraInfo("email", EMAIL).build())
                                        .build())
                    .build())
            .build();
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    when(scopeInfoHelper.getParentUniqueId(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID))).thenReturn("derivedParentId");

    handler.handleEvent(event);

    verify(scopeInfoHelper).getParentUniqueId(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID));
    verify(ciPipelineBaselineRespository)
        .upsert(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), eq("derivedParentId"),
            eq(PLAN_EXECUTION_ID), eq(1000L));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withFeatureFlagDisabled_shouldFallBackToAccountQuery() {
    List<CIStageOptimizationState> states =
        Arrays.asList(CIStageOptimizationState.builder().state(OPTIMIZATION_STATE_OPTIMIZED).build());
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, states);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    when(ciFeatureFlagService.isEnabled(any(), eq(ACCOUNT_ID))).thenReturn(false);
    when(ciPipelineBaselineRespository.findByAccountIdAndOrgIdAndProjectIdAndPipelineId(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID)))
        .thenReturn(null);

    handler.handleEvent(event);

    verify(ciPipelineBaselineRespository)
        .findByAccountIdAndOrgIdAndProjectIdAndPipelineId(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID));
    verify(ciPipelineBaselineRespository)
        .upsert(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), eq(PARENT_UNIQUE_ID), eq(null), eq(1000L));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withEmptyLists_shouldReturnDefaults() {
    List<CIStageTelemetryData> telemetryDataList = Collections.emptyList();
    List<CIStageSavingsInfo> savingsInfoList = Collections.emptyList();

    Map<String, Object> result = handler.getPipelineTelemetryData(telemetryDataList, savingsInfoList);

    assertThat(result.get("build_tools"))
        .as("Build tools should be empty for empty input")
        .isEqualTo(new ArrayList<>());
    assertThat(result.get("languages")).as("Languages should be empty for empty input").isEqualTo(new ArrayList<>());
    assertThat(result.get("total_time_saved")).as("Total time saved should be 0 for empty input").isEqualTo(0L);
    assertThat(result.get("bi_total_build_tasks")).as("Build tasks should be 0 for empty input").isEqualTo(0);
    assertThat(result.get("code_metrics_enabled"))
        .as("Code metrics should be disabled for empty input")
        .isEqualTo(false);
    assertThat(result.get("code_metrics_repositories_count")).as("Repository count should be 0").isEqualTo(0);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withBuildIntelligenceInfo_shouldAggregateBuildData() {
    CITelemetryInfo.BuildIntelligenceInfo buildInfo = CITelemetryInfo.BuildIntelligenceInfo.builder()
                                                          .buildTasks(10)
                                                          .tasksRestored(5)
                                                          .isBuildIntelEnabled(true)
                                                          .isBuildIntelOptimized(true)
                                                          .isMavenBIUsed(true)
                                                          .isGoBIUsed(true)
                                                          .stepTypes(Arrays.asList("Run", "Plugin"))
                                                          .errors(Arrays.asList("error1"))
                                                          .build();
    CITelemetryInfo telemetryInfo = CITelemetryInfo.builder()
                                        .buildIntelligenceInfo(buildInfo)
                                        .ciStepTypes(new java.util.HashSet<>(Arrays.asList("Run", "BuildAndPush")))
                                        .build();
    CIStageTelemetryData telemetryData =
        CIStageTelemetryData.builder().buildTool("Maven").language("Java").ciTelemetryInfo(telemetryInfo).build();

    List<CIStageSavingsInfo> savingsInfoList = Arrays.asList(CIStageSavingsInfo.builder().timeSaved(500L).build());

    Map<String, Object> result = handler.getPipelineTelemetryData(Arrays.asList(telemetryData), savingsInfoList);

    assertThat(result.get("bi_total_build_tasks")).as("Build tasks should reflect aggregated count").isEqualTo(10);
    assertThat(result.get("bi_total_tasks_restored")).as("Tasks restored should reflect aggregated count").isEqualTo(5);
    assertThat(result.get("bi_is_build_intel_enabled")).as("Build intel should be enabled").isEqualTo(true);
    assertThat(result.get("bi_is_maven_used")).as("Maven should be reported as used").isEqualTo(true);
    assertThat(result.get("bi_is_go_used")).as("Go should be reported as used").isEqualTo(true);
    assertThat(result.get("total_time_saved")).as("Total time saved should be sum of savings").isEqualTo(500L);
    assertThat((List<String>) result.get("ci_step_types"))
        .as("CI step types should include all unique types")
        .containsExactlyInAnyOrder("Run", "BuildAndPush");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withTestIntelligenceInfo_shouldAggregateTestData() {
    CITelemetryInfo.TestIntelligenceInfo testInfo = CITelemetryInfo.TestIntelligenceInfo.builder()
                                                        .totalTests(100)
                                                        .totalTestClasses(20)
                                                        .totalSelectedTests(50)
                                                        .totalSelectedTestClass(10)
                                                        .cpuTimeSaved(3000L)
                                                        .isRunTestV2(true)
                                                        .isRunTestV2Optimized(true)
                                                        .language(Arrays.asList(".java", ".kt"))
                                                        .build();
    CITelemetryInfo telemetryInfo = CITelemetryInfo.builder().testIntelligenceInfo(testInfo).build();
    CIStageTelemetryData telemetryData = CIStageTelemetryData.builder().ciTelemetryInfo(telemetryInfo).build();

    Map<String, Object> result =
        handler.getPipelineTelemetryData(Arrays.asList(telemetryData), Collections.emptyList());

    assertThat(result.get("ti_total_tests")).as("Total tests should be aggregated").isEqualTo(100);
    assertThat(result.get("ti_total_selected_tests")).as("Selected tests should be aggregated").isEqualTo(50);
    assertThat(result.get("ti_cpu_time_saved")).as("CPU time saved should be aggregated").isEqualTo(3000L);
    assertThat(result.get("is_run_test_v2")).as("RunTest V2 flag should be true").isEqualTo(true);
    assertThat((List<String>) result.get("ti_languages"))
        .as("TI languages should be mapped from extensions")
        .containsExactlyInAnyOrder("Java", "Kotlin");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withCacheIntelligenceInfo_shouldAggregateCacheData() {
    CITelemetryInfo.CacheIntelligenceInfo cacheInfo = CITelemetryInfo.CacheIntelligenceInfo.builder()
                                                          .cacheSize(1048576L)
                                                          .isCacheIntelEnabled(true)
                                                          .isCacheIntelOptimized(true)
                                                          .isNonDefaultPath(true)
                                                          .isCustomKeys(true)
                                                          .errors(Arrays.asList("cache_error"))
                                                          .build();
    CITelemetryInfo telemetryInfo = CITelemetryInfo.builder().cacheIntelligenceInfo(cacheInfo).build();
    CIStageTelemetryData telemetryData = CIStageTelemetryData.builder().ciTelemetryInfo(telemetryInfo).build();

    Map<String, Object> result =
        handler.getPipelineTelemetryData(Arrays.asList(telemetryData), Collections.emptyList());

    assertThat(result.get("cache_intel_is_cache_intel_enabled")).as("Cache intel should be enabled").isEqualTo(true);
    assertThat(result.get("cache_intel_is_non_default_path"))
        .as("Non default path flag should be true")
        .isEqualTo(true);
    assertThat(result.get("cache_intel_is_custom_keys")).as("Custom keys flag should be true").isEqualTo(true);
    assertThat(result.get("cache_intel_total_cache_size")).as("Cache size should be human readable").isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withDlcInfo_shouldAggregateDlcData() {
    CITelemetryInfo.DlcInfo dlcInfo = CITelemetryInfo.DlcInfo.builder()
                                          .totalLayers(10)
                                          .layersRestored(7)
                                          .isDlcEnabled(true)
                                          .isDlcOptimized(true)
                                          .errors(Arrays.asList("dlc_error"))
                                          .build();
    CITelemetryInfo telemetryInfo = CITelemetryInfo.builder().dlcInfo(dlcInfo).build();
    CIStageTelemetryData telemetryData = CIStageTelemetryData.builder().ciTelemetryInfo(telemetryInfo).build();

    Map<String, Object> result =
        handler.getPipelineTelemetryData(Arrays.asList(telemetryData), Collections.emptyList());

    assertThat(result.get("dlc_total_layers")).as("Total DLC layers should be aggregated").isEqualTo(10);
    assertThat(result.get("dlc_layers_restored")).as("DLC layers restored should be aggregated").isEqualTo(7);
    assertThat(result.get("dlc_is_dlc_enabled")).as("DLC enabled flag should be true").isEqualTo(true);
    assertThat(result.get("dlc_is_dlc_optimized")).as("DLC optimized flag should be true").isEqualTo(true);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withCodeMetrics_shouldAggregateCodeMetricsData() {
    Map<String, CITelemetryInfo.CodeMetricsInfo.LanguageMetrics> langMetrics = new HashMap<>();
    langMetrics.put("Java",
        CITelemetryInfo.CodeMetricsInfo.LanguageMetrics.builder()
            .lines(1000L)
            .code(800L)
            .comments(100L)
            .blanks(100L)
            .files(20L)
            .complexity(50L)
            .build());

    Map<String, CITelemetryInfo.CodeMetricsInfo> codeMetricsByRepo = new HashMap<>();
    codeMetricsByRepo.put("repo1",
        CITelemetryInfo.CodeMetricsInfo.builder()
            .repository("https://github.com/test/repo1")
            .buildEvent("push")
            .buildEventValue("main")
            .pluginVersion("1.0")
            .totalLines(1000L)
            .totalCode(800L)
            .totalComments(100L)
            .totalBlanks(100L)
            .totalFiles(20L)
            .totalComplexity(50L)
            .languageMetrics(langMetrics)
            .build());

    CITelemetryInfo telemetryInfo = CITelemetryInfo.builder().codeMetricsByRepository(codeMetricsByRepo).build();
    CIStageTelemetryData telemetryData = CIStageTelemetryData.builder().ciTelemetryInfo(telemetryInfo).build();

    Map<String, Object> result =
        handler.getPipelineTelemetryData(Arrays.asList(telemetryData), Collections.emptyList());

    assertThat(result.get("code_metrics_enabled"))
        .as("Code metrics should be enabled when there are repositories")
        .isEqualTo(true);
    assertThat(result.get("code_metrics_repositories_count")).as("Repository count should be 1").isEqualTo(1);
    assertThat((List<String>) result.get("code_metrics_repository_urls"))
        .as("Repository URLs should contain the repo URL")
        .hasSize(1);
    assertThat((List<String>) result.get("code_metrics_language_names"))
        .as("Language names should contain Java")
        .contains("Java");
    assertThat((List<String>) result.get("code_metrics_languages"))
        .as("Unique languages should contain Java")
        .contains("Java");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withNullTelemetryInfo_shouldSkipStage() {
    CIStageTelemetryData telemetryData =
        CIStageTelemetryData.builder().buildTool("Maven").language("Java").ciTelemetryInfo(null).build();

    Map<String, Object> result =
        handler.getPipelineTelemetryData(Arrays.asList(telemetryData), Collections.emptyList());

    assertThat((List<String>) result.get("build_tools"))
        .as("Build tools should still contain Maven from stage-level data")
        .contains("Maven");
    assertThat(result.get("bi_total_build_tasks"))
        .as("Build tasks should be 0 since telemetry info was null")
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withMultipleSavingsInfos_shouldSumTimeSaved() {
    List<CIStageSavingsInfo> savingsInfoList = Arrays.asList(CIStageSavingsInfo.builder().timeSaved(100L).build(),
        CIStageSavingsInfo.builder().timeSaved(200L).build(), CIStageSavingsInfo.builder().timeSaved(300L).build());

    Map<String, Object> result = handler.getPipelineTelemetryData(Collections.emptyList(), savingsInfoList);

    assertThat(result.get("total_time_saved")).as("Total time saved should be the sum of all savings").isEqualTo(600L);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_withStageTelemetryData_shouldIncludePipelineTelemetry() {
    CIPipelineModuleInfo moduleInfo =
        buildModuleInfo(false, Collections.emptyList(), null, Collections.emptyList(), null, null);
    Ambiance ambiance = buildAmbiance(1000L);
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).moduleInfo(moduleInfo).endTs(2000L).build();

    List<CIStageTelemetryData> telemetryDataList =
        Arrays.asList(CIStageTelemetryData.builder().buildTool("Maven").build());
    List<CIStageSavingsInfo> savingsInfoList = Arrays.asList(CIStageSavingsInfo.builder().timeSaved(100L).build());

    when(ciStageTelemetryRepository.findByPlanExecutionId(eq(PLAN_EXECUTION_ID))).thenReturn(telemetryDataList);
    when(ciStageSavingsInfoRepository.findByAccountIdAndPlanExecutionId(eq(ACCOUNT_ID), eq(PLAN_EXECUTION_ID)))
        .thenReturn(savingsInfoList);

    handler.handleEvent(event);

    verify(telemetryReporter).sendTrackEvent(eq("ci_built"), eq(EMAIL), eq(ACCOUNT_ID), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withUnknownLanguageExtension_shouldUseExtensionAsIs() {
    CITelemetryInfo.TestIntelligenceInfo testInfo =
        CITelemetryInfo.TestIntelligenceInfo.builder().language(Arrays.asList(".unknown")).build();
    CITelemetryInfo telemetryInfo = CITelemetryInfo.builder().testIntelligenceInfo(testInfo).build();
    CIStageTelemetryData telemetryData = CIStageTelemetryData.builder().ciTelemetryInfo(telemetryInfo).build();

    Map<String, Object> result =
        handler.getPipelineTelemetryData(Arrays.asList(telemetryData), Collections.emptyList());

    assertThat((List<String>) result.get("ti_languages"))
        .as("Unknown extension should be used as-is")
        .contains(".unknown");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPipelineTelemetryData_withNullCodeMetricsFields_shouldDefaultToZero() {
    Map<String, CITelemetryInfo.CodeMetricsInfo> codeMetricsByRepo = new HashMap<>();
    codeMetricsByRepo.put("repo1",
        CITelemetryInfo.CodeMetricsInfo.builder()
            .repository(null)
            .buildEvent(null)
            .buildEventValue(null)
            .pluginVersion(null)
            .totalLines(null)
            .totalCode(null)
            .totalComments(null)
            .totalBlanks(null)
            .totalFiles(null)
            .totalComplexity(null)
            .languageMetrics(null)
            .build());
    CITelemetryInfo telemetryInfo = CITelemetryInfo.builder().codeMetricsByRepository(codeMetricsByRepo).build();
    CIStageTelemetryData telemetryData = CIStageTelemetryData.builder().ciTelemetryInfo(telemetryInfo).build();

    Map<String, Object> result =
        handler.getPipelineTelemetryData(Arrays.asList(telemetryData), Collections.emptyList());

    assertThat((List<Long>) result.get("code_metrics_repository_lines"))
        .as("Lines should default to 0 when null")
        .containsExactly(0L);
    assertThat((List<Long>) result.get("code_metrics_repository_code"))
        .as("Code should default to 0 when null")
        .containsExactly(0L);
  }
}
